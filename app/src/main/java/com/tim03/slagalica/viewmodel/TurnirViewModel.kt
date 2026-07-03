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
    // Lives in the ViewModel (not the composable) so returning from a partija
    // doesn't show the join button again for a player already in the tournament.
    val joined: Boolean = false,
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
    private var joining = false
    private val navigatedSessions = mutableSetOf<String>()

    // Returns true only the first time a session id is seen, so the screen doesn't
    // re-navigate into an already played partija when it comes back on top.
    fun markNavigated(sessionId: String): Boolean = navigatedSessions.add(sessionId)

    fun joinTournament() {
        val myUid = auth.currentUser?.uid ?: return
        if (joining || _uiState.value.joined) return
        joining = true
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
                _uiState.value = _uiState.value.copy(joined = true)
                listenToQueue()
                listenForResult()
            }
            joining = false
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
                        // The bracket is deterministic: semi 1 is playerUids[0] vs [1],
                        // semi 2 is [2] vs [3], and player1 of each session is [0] / [2].
                        val inSemi1 = myUid == session.semi1Player1Uid() || myUid == session.semi1Player2Uid()
                        val mySemi = if (inSemi1) session.semi1SessionId else session.semi2SessionId
                        val isP1 = if (inSemi1) myUid == session.semi1Player1Uid()
                                   else myUid == session.semi2Player1Uid()
                        _uiState.value = current.copy(
                            session = session,
                            phase = TurnirPhase.SEMI_FINAL,
                            mySessionId = mySemi,
                            myIsPlayer1 = isP1
                        )
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
                    // The final session is created with player1 = semi1 winner, so the
                    // session id and player slot are set together with the phase - the
                    // screen navigates off a single consistent state.
                    _uiState.value = current.copy(
                        session = session,
                        phase = newPhase,
                        mySessionId = if (inFinal) session.finalSessionId else current.mySessionId,
                        myIsPlayer1 = if (inFinal) myUid == session.semi1Winner else current.myIsPlayer1
                    )
                }
                "completed" -> {
                    val won = session.tournamentWinner == myUid
                    val newPhase = when {
                        won -> TurnirPhase.WINNER
                        current.phase == TurnirPhase.ELIMINATED -> TurnirPhase.ELIMINATED
                        else -> TurnirPhase.RUNNER_UP
                    }
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
