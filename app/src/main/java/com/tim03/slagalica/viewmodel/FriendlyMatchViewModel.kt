package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.repository.FriendInvite
import com.tim03.slagalica.data.repository.FriendInviteRepository
import com.tim03.slagalica.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FriendlyPhase {
    SEARCH,        // entering username to search
    INVITE_SENT,   // waiting for receiver to respond
    ACCEPTED,      // session created, ready to navigate
    REJECTED,      // invite was rejected or auto-rejected
    ERROR
}

data class FriendlyMatchUiState(
    val phase: FriendlyPhase = FriendlyPhase.SEARCH,
    val searchQuery: String = "",
    val foundName: String = "",
    val foundUid: String = "",
    val sessionId: String = "",
    val isPlayer1: Boolean = true,
    val errorMessage: String = "",
    val secondsLeft: Int = 10
)

class FriendlyMatchViewModel(
    private val repo: FriendInviteRepository = FriendInviteRepository(),
    private val userRepo: UserRepository = UserRepository()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(FriendlyMatchUiState())
    val uiState: StateFlow<FriendlyMatchUiState> = _uiState.asStateFlow()

    private var currentInviteId = ""
    private var responseListener: ListenerRegistration? = null
    private var autoRejectJob: Job? = null
    private var autoInvited = false

    // Opened from the friends list: the friend is already known, so skip the search
    // and send the invite right away. On a later retry (after a rejection) the friend
    // stays preselected but the invite is sent only when the user taps the button again.
    fun preselectFriend(uid: String, name: String) {
        if (uid.isEmpty()) return
        val myUid = auth.currentUser?.uid ?: ""
        if (uid == myUid) return
        _uiState.value = _uiState.value.copy(
            foundUid = uid, foundName = name, searchQuery = name, errorMessage = ""
        )
        if (!autoInvited) {
            autoInvited = true
            sendInvite()
        }
    }

    fun onSearchQueryChange(q: String) {
        _uiState.value = _uiState.value.copy(searchQuery = q, foundUid = "", foundName = "")
    }

    fun searchUser() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val result = repo.findUserByUsername(query)
                if (result == null) {
                    _uiState.value = _uiState.value.copy(errorMessage = "Korisnik nije pronađen", phase = FriendlyPhase.ERROR)
                } else {
                    val myUid = auth.currentUser?.uid ?: ""
                    if (result.first == myUid) {
                        _uiState.value = _uiState.value.copy(errorMessage = "Ne možete izazvati sami sebe", phase = FriendlyPhase.ERROR)
                    } else {
                        _uiState.value = _uiState.value.copy(foundUid = result.first, foundName = result.second, errorMessage = "")
                    }
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = "Greška pri pretrazi", phase = FriendlyPhase.ERROR)
            }
        }
    }

    fun sendInvite() {
        val myUid = auth.currentUser?.uid ?: return
        val s = _uiState.value
        if (s.foundUid.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val myUser = userRepo.getCurrentUser() ?: return@runCatching
                val inviteId = repo.sendInvite(myUid, myUser.username, s.foundUid)
                currentInviteId = inviteId
                _uiState.value = _uiState.value.copy(phase = FriendlyPhase.INVITE_SENT, secondsLeft = 10)
                listenForResponse(inviteId)
                startAutoRejectTimer()
            }
        }
    }

    private fun listenForResponse(inviteId: String) {
        responseListener?.remove()
        responseListener = repo.listenForInviteResponse(inviteId) { invite ->
            when (invite.status) {
                "accepted" -> {
                    autoRejectJob?.cancel()
                    val myUid = auth.currentUser?.uid ?: ""
                    _uiState.value = _uiState.value.copy(
                        phase = FriendlyPhase.ACCEPTED,
                        sessionId = invite.sessionId,
                        isPlayer1 = invite.senderId == myUid
                    )
                }
                "rejected" -> {
                    autoRejectJob?.cancel()
                    _uiState.value = _uiState.value.copy(phase = FriendlyPhase.REJECTED)
                }
                "cancelled" -> {
                    autoRejectJob?.cancel()
                    _uiState.value = _uiState.value.copy(phase = FriendlyPhase.REJECTED)
                }
            }
        }
    }

    private fun startAutoRejectTimer() {
        autoRejectJob?.cancel()
        autoRejectJob = viewModelScope.launch {
            repeat(10) { i ->
                delay(1000)
                _uiState.value = _uiState.value.copy(secondsLeft = 9 - i)
            }
            if (_uiState.value.phase == FriendlyPhase.INVITE_SENT) {
                runCatching { repo.rejectInvite(currentInviteId) }
                _uiState.value = _uiState.value.copy(phase = FriendlyPhase.REJECTED)
            }
        }
    }

    fun cancel() {
        autoRejectJob?.cancel()
        responseListener?.remove()
        if (currentInviteId.isNotEmpty()) {
            viewModelScope.launch { runCatching { repo.cancelInvite(currentInviteId) } }
        }
        _uiState.value = FriendlyMatchUiState()
        currentInviteId = ""
    }

    fun reset() {
        _uiState.value = FriendlyMatchUiState()
    }

    override fun onCleared() {
        super.onCleared()
        autoRejectJob?.cancel()
        responseListener?.remove()
    }
}
