package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.repository.FriendInvite
import com.tim03.slagalica.data.repository.FriendInviteRepository
import com.tim03.slagalica.data.repository.NotificationRepository
import com.tim03.slagalica.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val username: String = "",
    val tokens: Int = 0,
    val stars: Int = 0,
    val league: Int = 0,
    val avatarIndex: Int = 0,
    val isGuest: Boolean = false,
    val incomingInvite: FriendInvite? = null,
    val myRegion: String = ""
)

class HomeViewModel(
    private val userRepo: UserRepository = UserRepository(),
    private val friendRepo: FriendInviteRepository = FriendInviteRepository(),
    private val notifRepo: NotificationRepository = NotificationRepository()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var inviteListener: ListenerRegistration? = null
    private var autoRejectJob: Job? = null

    init {
        loadUser()
    }

    fun loadUser() {
        if (auth.currentUser?.isAnonymous == true) {
            _uiState.value = HomeUiState(
                username = auth.currentUser?.displayName ?: "Guest",
                isGuest = true
            )
            return
        }
        viewModelScope.launch {
            runCatching {
                val user = userRepo.getCurrentUser() ?: return@runCatching
                _uiState.value = HomeUiState(
                    username = user.username,
                    tokens = user.tokens,
                    stars = user.stars,
                    league = user.league,
                    avatarIndex = user.avatarIndex,
                    myRegion = user.region
                )
                // Daily token refresh
                val earned = userRepo.checkAndRefreshDailyTokens()
                if (earned > 0) {
                    notifRepo.addNotification(
                        user.uid, "REWARD", "Dnevni tokeni!",
                        "Dobili ste $earned tokena za danas. Srećno!"
                    )
                    _uiState.value = _uiState.value.copy(tokens = user.tokens + earned)
                }
                // Start listening for friend invites
                listenForInvites(user.uid)
            }
        }
    }

    private fun listenForInvites(myUid: String) {
        inviteListener?.remove()
        inviteListener = friendRepo.listenForIncomingInvites(myUid) { invite ->
            if (invite == null) {
                autoRejectJob?.cancel()
                _uiState.value = _uiState.value.copy(incomingInvite = null)
                return@listenForIncomingInvites
            }
            if (_uiState.value.incomingInvite?.id == invite.id) return@listenForIncomingInvites
            _uiState.value = _uiState.value.copy(incomingInvite = invite)
            startAutoReject(invite.id)
        }
    }

    private fun startAutoReject(inviteId: String) {
        autoRejectJob?.cancel()
        autoRejectJob = viewModelScope.launch {
            delay(10_000)
            if (_uiState.value.incomingInvite?.id == inviteId) {
                rejectInvite()
            }
        }
    }

    fun acceptInvite(onSessionReady: (sessionId: String, isPlayer1: Boolean) -> Unit) {
        val invite = _uiState.value.incomingInvite ?: return
        val myUid = auth.currentUser?.uid ?: return
        autoRejectJob?.cancel()
        _uiState.value = _uiState.value.copy(incomingInvite = null)
        viewModelScope.launch {
            runCatching {
                val user = userRepo.getCurrentUser() ?: return@runCatching
                val sessionId = friendRepo.acceptInvite(
                    inviteId = invite.id,
                    senderId = invite.senderId,
                    senderName = invite.senderName,
                    receiverId = myUid,
                    receiverName = user.username
                )
                onSessionReady(sessionId, false) // receiver is always player2
            }
        }
    }

    fun rejectInvite() {
        val invite = _uiState.value.incomingInvite ?: return
        autoRejectJob?.cancel()
        _uiState.value = _uiState.value.copy(incomingInvite = null)
        viewModelScope.launch { runCatching { friendRepo.rejectInvite(invite.id) } }
    }

    fun logout() {
        inviteListener?.remove()
        autoRejectJob?.cancel()
        val user = auth.currentUser
        if (user?.isAnonymous == true) {
            user.delete().addOnCompleteListener { auth.signOut() }
        } else {
            auth.signOut()
        }
    }

    override fun onCleared() {
        super.onCleared()
        inviteListener?.remove()
        autoRejectJob?.cancel()
    }
}
