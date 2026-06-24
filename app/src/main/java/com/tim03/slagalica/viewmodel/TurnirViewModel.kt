package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.model.TurnirSession
import com.tim03.slagalica.data.repository.DailyMissionsRepository
import com.tim03.slagalica.data.repository.TurnirRepository
import com.tim03.slagalica.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TurnirPhase {
    WAITING,          // in matchmaking queue
    SEMI_FINAL,       // playing semi-final partija
    WAITING_FOR_FINAL,// semi-final done, waiting for other semi to finish
    FINAL,            // playing final partija
    ELIMINATED,       // lost semi-final
    WINNER,           // won final
    RUNNER_UP         // lost final
}

data class TurnirUiState(
    val phase: TurnirPhase = TurnirPhase.WAITING,
    val turnirId: String = "",
    val session: TurnirSession? = null,
    val myUid: String = "",
    val mySessionId: String = "",
    val myIsPlayer1: Boolean = true,
    val queueCount: Int = 1,
    val error: String? = null
)

class TurnirViewModel(
    private val repo: TurnirRepository = TurnirRepository(),
    private val userRepo: UserRepository = UserRepository(),
    private val missionRepo: DailyMissionsRepository = DailyMissionsRepository()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(TurnirUiState())
    val uiState: StateFlow<TurnirUiState> = _uiState.asStateFlow()

    private var queueListener: ListenerRegistration? = null
    private var resultListener: ListenerRegistration? = null
    private var turnirListener: ListenerRegistration? = null

    fun joinTournament() {
        val myUid = auth.currentUser?.uid ?: return
        _uiState.value = _uiState.value.copy(myUid = myUid)
        viewModelScope.launch {
            runCatching {
                val user = userRepo.getCurrentUser() ?: return@runCatching
                if (user.tokens < 3) {
                    _uiState.value = _uiState.value.copy(error = "Nemate dovoljno tokena (potrebno 3)")
                    return@runCatching
                }
                // Deduct 3 tokens
                userRepo.addTokens(myUid, -3)
                repo.joinQueue(
                    uid = myUid,
                    username = user.username,
                    league = user.league,
                    avatarIndex = user.avatarIndex
                )
                listenToQueue()
                listenForResult()
            }
        }
    }

    private fun listenToQueue() {
        val myUid = _uiState.value.myUid
        queueListener = repo.listenToQueue(myUid) { players ->
            if (_uiState.value.phase != TurnirPhase.WAITING) return@listenToQueue
            _uiState.value = _uiState.value.copy(queueCount = players.size)
            if (players.size >= 4 && _uiState.value.turnirId.isEmpty()) {
                // All players attempt creation; Firestore transaction ensures only one succeeds
                viewModelScope.launch {
                    repo.tryCreateTournament(players)
                }
            }
        }
    }

    private fun listenForResult() {
        val myUid = _uiState.value.myUid
        resultListener = repo.listenForMyResult(myUid) { turnirId ->
            if (_uiState.value.turnirId.isNotEmpty()) return@listenForMyResult
            _uiState.value = _uiState.value.copy(turnirId = turnirId)
            viewModelScope.launch { repo.deleteMyResult(myUid) }
            queueListener?.remove()
            listenToTurnir(turnirId)
        }
    }

    private fun listenToTurnir(turnirId: String) {
        turnirListener = repo.listenToTurnir(turnirId) { session ->
            val myUid = _uiState.value.myUid
            val current = _uiState.value

            when (session.status) {
                "semifinal" -> {
                    if (current.phase == TurnirPhase.WAITING) {
                        val mySemi = if (myUid == session.semi1Player1Uid() || myUid == session.semi1Player2Uid())
                            session.semi1SessionId else session.semi2SessionId
                        _uiState.value = current.copy(
                            session = session,
                            phase = TurnirPhase.SEMI_FINAL,
                            mySessionId = mySemi
                        )
                        viewModelScope.launch {
                            val isP1 = repo.getSessionIsPlayer1(mySemi, myUid)
                            _uiState.value = _uiState.value.copy(myIsPlayer1 = isP1)
                        }
                    }
                }
                "final" -> {
                    val inFinal = session.finalSessionId.isNotEmpty() &&
                            (session.semi1Winner == myUid || session.semi2Winner == myUid)
                    val eliminated = !inFinal && (
                            session.semi1Winner.isNotEmpty() && session.semi1Winner != myUid &&
                            (myUid == session.semi1Player1Uid() || myUid == session.semi1Player2Uid()) ||
                            session.semi2Winner.isNotEmpty() && session.semi2Winner != myUid &&
                            (myUid == session.semi2Player1Uid() || myUid == session.semi2Player2Uid())
                    )
                    val newPhase = when {
                        eliminated -> TurnirPhase.ELIMINATED
                        inFinal -> TurnirPhase.FINAL
                        else -> TurnirPhase.WAITING_FOR_FINAL
                    }
                    _uiState.value = current.copy(session = session, phase = newPhase)
                    if (inFinal && newPhase == TurnirPhase.FINAL) {
                        viewModelScope.launch {
                            val isP1 = repo.getSessionIsPlayer1(session.finalSessionId, myUid)
                            _uiState.value = _uiState.value.copy(
                                mySessionId = session.finalSessionId,
                                myIsPlayer1 = isP1
                            )
                        }
                    }
                }
                "completed" -> {
                    val won = session.tournamentWinner == myUid
                    val newPhase = if (won) TurnirPhase.WINNER else TurnirPhase.RUNNER_UP
                    _uiState.value = current.copy(session = session, phase = newPhase)
                    if (won) {
                        viewModelScope.launch {
                            missionRepo.completeWinTournament()
                        }
                    }
                }
            }
        }
    }

    fun onSemiFinalComplete(won: Boolean, myScore: Int, opponentScore: Int) {
        val session = _uiState.value.session ?: return
        val myUid = _uiState.value.myUid
        val winnerUid = if (won) myUid else {
            val isInSemi1 = myUid == session.semi1Player1Uid() || myUid == session.semi1Player2Uid()
            if (isInSemi1) {
                if (myUid == session.semi1Player1Uid()) session.semi1Player2Uid() else session.semi1Player1Uid()
            } else {
                if (myUid == session.semi2Player1Uid()) session.semi2Player2Uid() else session.semi2Player1Uid()
            }
        }
        val isInSemi1 = myUid == session.semi1Player1Uid() || myUid == session.semi1Player2Uid()
        val semiField = if (isInSemi1) "semi1Winner" else "semi2Winner"

        // Award 2 tokens to semi-final winner
        if (won) {
            viewModelScope.launch {
                runCatching {
                    userRepo.addTokens(myUid, 2)
                }
            }
        }

        viewModelScope.launch {
            runCatching {
                repo.reportSemiFinalResult(_uiState.value.turnirId, semiField, winnerUid)
            }
        }
    }

    fun onFinalComplete(won: Boolean) {
        val turnirId = _uiState.value.turnirId
        val myUid = _uiState.value.myUid
        val session = _uiState.value.session ?: return
        val loserUid = if (won) {
            session.finalSessionId.let { if (session.semi1Winner == myUid) session.semi2Winner else session.semi1Winner }
        } else myUid

        if (won) {
            viewModelScope.launch {
                runCatching { repo.reportFinalResult(turnirId, myUid, loserUid) }
            }
        }
    }

    fun leave() {
        val myUid = _uiState.value.myUid
        viewModelScope.launch { runCatching { repo.leaveQueue(myUid) } }
        cleanup()
    }

    private fun cleanup() {
        queueListener?.remove()
        resultListener?.remove()
        turnirListener?.remove()
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
