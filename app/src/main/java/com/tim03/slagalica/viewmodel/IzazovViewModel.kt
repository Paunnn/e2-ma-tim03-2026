package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.model.IzazovSession
import com.tim03.slagalica.data.repository.IzazovRepository
import com.tim03.slagalica.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class IzazovPhase { LIST, CREATING, RESULTS }

data class IzazovUiState(
    val phase: IzazovPhase = IzazovPhase.LIST,
    val openSessions: List<IzazovSession> = emptyList(),
    val activeSession: IzazovSession? = null,
    val activeIzazovId: String = "",
    val createBidStars: Int = 0,
    val createBidTokens: Int = 0,
    // One-shot navigation event: when set, the screen navigates to the solo partija
    // for this izazov (spec 9d - every game appears once) and clears it.
    val playIzazovId: String? = null,
    val myUid: String = "",
    val myRegion: String = "",
    val myTokens: Int = 0,
    val myStars: Int = 0
)

class IzazovViewModel(
    private val repo: IzazovRepository = IzazovRepository(),
    private val userRepo: UserRepository = UserRepository()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(IzazovUiState())
    val uiState: StateFlow<IzazovUiState> = _uiState.asStateFlow()

    private var listListener: ListenerRegistration? = null
    private var sessionListener: ListenerRegistration? = null

    init {
        loadUser()
    }

    private fun loadUser() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching {
                val user = userRepo.getCurrentUser() ?: return@runCatching
                val region = user.region.ifEmpty { "Srbija" }
                _uiState.value = _uiState.value.copy(
                    myUid = user.uid,
                    myRegion = region,
                    myTokens = user.tokens,
                    myStars = user.stars
                )
                startListeningToOpenSessions(region)
            }
        }
    }

    private fun startListeningToOpenSessions(region: String = _uiState.value.myRegion) {
        val uid = _uiState.value.myUid.ifEmpty { auth.currentUser?.uid ?: "" }
        listListener?.remove()
        listListener = repo.listenToOpenSessions(region, uid) { sessions ->
            _uiState.value = _uiState.value.copy(openSessions = sessions)
            // Any client that notices an expired challenge settles it; the repository's
            // transaction guard ensures only one of them actually pays out.
            sessions.filter { it.status == "open" && it.isExpired() }.forEach { expired ->
                viewModelScope.launch { runCatching { repo.finalize(expired.id) } }
            }
        }
    }

    // Poster closes the challenge early: with 2+ players who all played it pays out,
    // alone it cancels and refunds the stake.
    fun finalizeEarly(session: IzazovSession) {
        viewModelScope.launch { runCatching { repo.finalize(session.id, byPosterEarly = true) } }
    }

    fun setBidStars(value: Int) {
        _uiState.value = _uiState.value.copy(createBidStars = value.coerceIn(0, 10))
    }

    fun setBidTokens(value: Int) {
        _uiState.value = _uiState.value.copy(createBidTokens = value.coerceIn(0, 2))
    }

    fun createIzazov() {
        val uid = auth.currentUser?.uid ?: return
        val s = _uiState.value
        if (s.createBidStars == 0 && s.createBidTokens == 0) return
        _uiState.value = s.copy(phase = IzazovPhase.CREATING)
        viewModelScope.launch {
            runCatching {
                val user = userRepo.getCurrentUser() ?: return@runCatching
                if (user.stars < s.createBidStars || user.tokens < s.createBidTokens) return@runCatching
                val region = user.region.ifEmpty { "Srbija" }
                val izazovId = repo.createIzazov(uid, user.username, region, s.createBidStars, s.createBidTokens)
                _uiState.value = _uiState.value.copy(
                    phase = IzazovPhase.LIST,
                    playIzazovId = izazovId,
                    myUid = uid
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(phase = IzazovPhase.LIST)
            }
        }
    }

    fun joinIzazov(session: IzazovSession) {
        val uid = auth.currentUser?.uid ?: return
        if (!session.canJoin(uid)) return
        viewModelScope.launch {
            runCatching {
                val user = userRepo.getCurrentUser() ?: return@runCatching
                if (user.stars < session.bidStars || user.tokens < session.bidTokens) return@runCatching
                repo.joinIzazov(session.id, uid, user.username, session.bidStars, session.bidTokens)
                _uiState.value = _uiState.value.copy(playIzazovId = session.id, myUid = uid)
            }
        }
    }

    fun playExisting(session: IzazovSession) {
        val uid = auth.currentUser?.uid ?: return
        _uiState.value = _uiState.value.copy(playIzazovId = session.id, myUid = uid)
    }

    // Called by the screen once it has handled the navigation event.
    fun consumePlayEvent() {
        _uiState.value = _uiState.value.copy(playIzazovId = null)
    }

    fun viewResults(session: IzazovSession) {
        val uid = auth.currentUser?.uid ?: return
        sessionListener?.remove()
        sessionListener = repo.listenToSession(session.id) { updated ->
            _uiState.value = _uiState.value.copy(activeSession = updated)
        }
        _uiState.value = _uiState.value.copy(
            phase = IzazovPhase.RESULTS,
            activeSession = session,
            activeIzazovId = session.id,
            myUid = uid
        )
    }

    fun backToList() {
        sessionListener?.remove()
        sessionListener = null
        _uiState.value = _uiState.value.copy(
            phase = IzazovPhase.LIST,
            activeSession = null,
            activeIzazovId = ""
        )
        loadUser()
    }

    // Called when the screen comes back into focus (e.g. returning from the partija)
    // so the balance and session list reflect the just-submitted score.
    fun refresh() {
        loadUser()
    }

    override fun onCleared() {
        super.onCleared()
        listListener?.remove()
        sessionListener?.remove()
    }
}
