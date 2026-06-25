package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.repository.MatchmakingRepository
import com.tim03.slagalica.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MatchmakingStatus { SEARCHING, MATCHED, CANCELLED, ERROR }

data class MatchmakingUiState(
    val status: MatchmakingStatus = MatchmakingStatus.SEARCHING,
    val sessionId: String = "",
    val isPlayer1: Boolean = true,
    val opponentName: String = "",
    val errorMessage: String? = null
)

class MatchmakingViewModel(
    private val matchmakingRepo: MatchmakingRepository = MatchmakingRepository(),
    private val userRepo: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchmakingUiState())
    val uiState: StateFlow<MatchmakingUiState> = _uiState.asStateFlow()

    private var myUid = ""
    private var myName = ""
    private var queueListener: ListenerRegistration? = null
    private var resultListener: ListenerRegistration? = null
    private var alreadyMatched = false

    init {
        startMatchmaking()
    }

    private fun startMatchmaking() {
        viewModelScope.launch {
            runCatching {
                val user = userRepo.getCurrentUser() ?: return@runCatching
                myUid = user.uid
                myName = user.username

                if (user.tokens < 1) {
                    _uiState.value = _uiState.value.copy(
                        status = MatchmakingStatus.ERROR,
                        errorMessage = "Nemate dovoljno tokena. Partija košta 1 token."
                    )
                    return@runCatching
                }

                // Deduct 1 token for entering matchmaking
                userRepo.addTokens(myUid, -1)

                // Clean up any leftover result doc from a previous session.
                matchmakingRepo.deleteMyResult(myUid)

                matchmakingRepo.joinQueue(myUid, myName)
                listenForMatch()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    status = MatchmakingStatus.ERROR,
                    errorMessage = e.message
                )
            }
        }
    }

    private fun listenForMatch() {
        // Listen for our own result document — written atomically for both players in tryCreateSession.
        // This fires for the LOSING device (the one that didn't create the session),
        // and also fires for the creator (but alreadyMatched prevents double-navigation).
        resultListener = matchmakingRepo.listenForMyResult(myUid) { sessionId, isPlayer1, opponentName ->
            if (alreadyMatched) return@listenForMyResult
            alreadyMatched = true
            queueListener?.remove()
            viewModelScope.launch { matchmakingRepo.deleteMyResult(myUid) }
            _uiState.value = MatchmakingUiState(
                status = MatchmakingStatus.MATCHED,
                sessionId = sessionId,
                isPlayer1 = isPlayer1,
                opponentName = opponentName
            )
        }

        // Watch the queue: first device to see an opponent tries to create the session.
        // On success it navigates immediately via the result doc listener above.
        queueListener = matchmakingRepo.listenToQueue(myUid) { opponentUid, opponentName ->
            if (alreadyMatched) return@listenToQueue
            viewModelScope.launch {
                // tryCreateSession writes result docs for both players atomically.
                // If we win, our resultListener fires and handles navigation.
                // If we lose (null return), the other device already created the session
                // and wrote our result doc — our resultListener will fire from that.
                matchmakingRepo.tryCreateSession(myUid, myName, opponentUid, opponentName)
            }
        }
    }

    fun cancel() {
        alreadyMatched = true
        queueListener?.remove()
        resultListener?.remove()
        _uiState.value = _uiState.value.copy(status = MatchmakingStatus.CANCELLED)
        viewModelScope.launch { runCatching { matchmakingRepo.leaveQueue(myUid) } }
    }

    override fun onCleared() {
        super.onCleared()
        queueListener?.remove()
        resultListener?.remove()
        if (myUid.isNotEmpty() && _uiState.value.status == MatchmakingStatus.SEARCHING) {
            viewModelScope.launch { runCatching { matchmakingRepo.leaveQueue(myUid) } }
        }
    }
}
