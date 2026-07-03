package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.repository.DailyMissionsRepository
import com.tim03.slagalica.data.repository.IzazovRepository
import com.tim03.slagalica.data.repository.NotificationRepository
import com.tim03.slagalica.data.repository.PartijaSessionRepository
import com.tim03.slagalica.data.repository.TurnirRepository
import com.tim03.slagalica.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PartijaGame {
    KO_ZNA_ZNA, SPOJNICE, MOJ_BROJ, KORAK_PO_KORAK, ASOCIJACIJE, SKOCKO
}

data class PartijaUiState(
    val currentGameIndex: Int = 0,
    val myTotal: Int = 0,
    val oppTotal: Int = 0,
    val opponentName: String = "Protivnik",
    val isComplete: Boolean = false,
    val resultSaved: Boolean = false,
    val waitingForOpponent: Boolean = false,
    val forfeitWon: Boolean = false,
    val forfeitLoss: Boolean = false,
    val opponentLeft: Boolean = false
)

class PartijaViewModel(
    val sessionId: String = "",
    val isPlayer1: Boolean = true,
    var isFriendly: Boolean = false,
    // Non-empty when this is a solo partija played as an izazov entry (spec 9d):
    // the final total is submitted as the izazov score instead of the usual rewards.
    val izazovId: String = "",
    private val userRepo: UserRepository = UserRepository(),
    private val sessionRepo: PartijaSessionRepository = PartijaSessionRepository(),
    private val missionRepo: DailyMissionsRepository = DailyMissionsRepository(),
    private val notifRepo: NotificationRepository = NotificationRepository(),
    private val turnirRepo: TurnirRepository = TurnirRepository(),
    private val izazovRepo: IzazovRepository = IzazovRepository()
) : ViewModel() {

    private var sessionTurnirId: String = ""
    private var sessionIsFinal: Boolean = false
    private var sessionIsFriendlyLoaded: Boolean = false

    companion object {
        val GAME_ORDER = listOf(
            PartijaGame.KO_ZNA_ZNA,
            PartijaGame.SPOJNICE,
            PartijaGame.MOJ_BROJ,
            PartijaGame.KORAK_PO_KORAK,
            PartijaGame.ASOCIJACIJE,
            PartijaGame.SKOCKO
        )
    }

    val isMultiplayer = sessionId.isNotEmpty()

    private val _uiState = MutableStateFlow(PartijaUiState())
    val uiState: StateFlow<PartijaUiState> = _uiState.asStateFlow()

    private var sessionListener: ListenerRegistration? = null

    init {
        if (isMultiplayer) listenToSession()
    }

    private fun listenToSession() {
        sessionListener = sessionRepo.listenToSession(sessionId) { session ->
            // Capture session metadata on first update
            if (sessionTurnirId.isEmpty() && session.turnirId.isNotEmpty()) {
                sessionTurnirId = session.turnirId
                sessionIsFinal = session.isFinal
            }
            if (!sessionIsFriendlyLoaded && session.isFriendly) {
                sessionIsFriendlyLoaded = true
                isFriendly = true
            }

            val s = _uiState.value
            if (s.isComplete) return@listenToSession

            val oppName = if (isPlayer1) session.player2Name else session.player1Name

            if (session.status == "forfeited" && session.forfeitedBy.isNotEmpty()) {
                val iForfeited = (isPlayer1 && session.forfeitedBy == "player1") ||
                                  (!isPlayer1 && session.forfeitedBy == "player2")
                if (iForfeited) {
                    // My own forfeit() already set this locally; keep it in sync either way.
                    _uiState.value = s.copy(
                        isComplete = true,
                        waitingForOpponent = false,
                        opponentName = oppName,
                        forfeitLoss = true
                    )
                    return@listenToSession
                }
                // Opponent left - I keep playing the partija; just stop waiting on scores
                // that will never arrive and skip straight to the next game.
                if (s.opponentLeft) {
                    _uiState.value = s.copy(opponentName = oppName)
                    return@listenToSession
                }
                if (s.waitingForOpponent) {
                    val nextIndex = s.currentGameIndex + 1
                    _uiState.value = if (nextIndex >= GAME_ORDER.size) {
                        s.copy(isComplete = true, waitingForOpponent = false, opponentName = oppName,
                               opponentLeft = true, forfeitWon = true)
                    } else {
                        s.copy(currentGameIndex = nextIndex, waitingForOpponent = false,
                               opponentName = oppName, opponentLeft = true)
                    }
                } else {
                    _uiState.value = s.copy(opponentName = oppName, opponentLeft = true)
                }
                return@listenToSession
            }

            if (s.waitingForOpponent) {
                val gameKey = s.currentGameIndex.toString()
                val oppScores = if (isPlayer1) session.player2GameScores else session.player1GameScores
                val oppScore = oppScores[gameKey]

                if (oppScore != null) {
                    val newOpp = s.oppTotal + oppScore.toInt()
                    val nextIndex = s.currentGameIndex + 1
                    _uiState.value = if (nextIndex >= GAME_ORDER.size) {
                        s.copy(oppTotal = newOpp, isComplete = true, waitingForOpponent = false, opponentName = oppName)
                    } else {
                        s.copy(currentGameIndex = nextIndex, oppTotal = newOpp, waitingForOpponent = false, opponentName = oppName)
                    }
                } else {
                    _uiState.value = s.copy(opponentName = oppName)
                }
            } else {
                _uiState.value = s.copy(opponentName = oppName)
            }
        }
    }

    fun forfeit() {
        val s = _uiState.value
        if (s.isComplete) return
        _uiState.value = s.copy(isComplete = true, waitingForOpponent = false, forfeitLoss = true)
        viewModelScope.launch {
            runCatching { sessionRepo.forfeit(sessionId, isPlayer1) }
        }
    }

    // Called by each game screen when it finishes.
    // In multiplayer, myScore is submitted to Firestore and oppScore is ignored
    // (the real opponent score arrives via the session listener).
    fun gameCompleted(myScore: Int, oppScore: Int) {
        val s = _uiState.value
        if (isMultiplayer) {
            val newMy = s.myTotal + myScore
            if (s.opponentLeft) {
                // Opponent is gone - no score will ever arrive for them, so don't wait.
                val nextIndex = s.currentGameIndex + 1
                _uiState.value = if (nextIndex >= GAME_ORDER.size) {
                    s.copy(myTotal = newMy, isComplete = true, forfeitWon = true)
                } else {
                    s.copy(currentGameIndex = nextIndex, myTotal = newMy)
                }
            } else {
                _uiState.value = s.copy(myTotal = newMy, waitingForOpponent = true)
            }
            viewModelScope.launch {
                runCatching {
                    sessionRepo.submitGameScore(sessionId, isPlayer1, s.currentGameIndex, myScore)
                }
            }
        } else {
            val newMy = s.myTotal + myScore
            val newOpp = s.oppTotal + oppScore
            val nextIndex = s.currentGameIndex + 1
            _uiState.value = if (nextIndex >= GAME_ORDER.size) {
                s.copy(myTotal = newMy, oppTotal = newOpp, isComplete = true)
            } else {
                s.copy(currentGameIndex = nextIndex, myTotal = newMy, oppTotal = newOpp)
            }
        }
    }

    fun saveResult() {
        val s = _uiState.value
        if (s.resultSaved) return
        _uiState.value = s.copy(resultSaved = true)

        // Izazov entry: the whole partija counts only toward the challenge score.
        // Stars/tokens are settled by the izazov pot (75% winner / refund runner-up),
        // not by the regular partija reward flow.
        if (izazovId.isNotEmpty()) {
            viewModelScope.launch {
                runCatching {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@runCatching
                    izazovRepo.submitScore(izazovId, uid, s.myTotal)
                }
            }
            return
        }

        val won = when {
            s.forfeitWon -> true
            s.forfeitLoss -> false
            else -> s.myTotal > s.oppTotal
        }
        val drawn = !s.forfeitWon && !s.forfeitLoss && s.myTotal == s.oppTotal
        viewModelScope.launch {
            runCatching {
                userRepo.savePartijaResult(won, drawn)
                if (isMultiplayer && !s.forfeitLoss) sessionRepo.completeSession(sessionId)

                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@runCatching

                if (!isFriendly) {
                    val awardResult = userRepo.awardStarsFromPartija(won, s.myTotal, isFriendly = false)
                    if (awardResult.tokensEarned > 0) {
                        notifRepo.addNotification(uid, "REWARD", "Bonus token!",
                            "Sakupili ste 50 zvezda i dobili ${awardResult.tokensEarned} token!")
                    }
                    if (won) missionRepo.completeWinGame()
                } else {
                    missionRepo.completePlayFriendly()
                }

                // Advance tournament bracket if this was a tournament session
                if (sessionTurnirId.isNotEmpty()) {
                    val winnerUid = if (won) uid else {
                        // Determine opponent UID from the session
                        val doc = sessionRepo.getSession(sessionId)
                        if (isPlayer1) doc?.player2Uid ?: "" else doc?.player1Uid ?: ""
                    }
                    if (sessionIsFinal) {
                        val loserUid = if (won) {
                            val doc = sessionRepo.getSession(sessionId)
                            if (isPlayer1) doc?.player2Uid ?: "" else doc?.player1Uid ?: ""
                        } else uid
                        runCatching { turnirRepo.reportFinalResult(sessionTurnirId, winnerUid, loserUid) }
                    } else {
                        val turnirDoc = turnirRepo.getTurnirSession(sessionTurnirId)
                        val semiField = if (turnirDoc?.semi1SessionId == sessionId) "semi1Winner" else "semi2Winner"
                        runCatching { turnirRepo.reportSemiFinalResult(sessionTurnirId, semiField, winnerUid) }
                        // Semi-final winner gets 2 tokens (spec 10d); only the winning
                        // client takes this branch so it can't be awarded twice.
                        if (won) runCatching { userRepo.addTokens(uid, 2) }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionListener?.remove()
    }
}

class PartijaViewModelFactory(
    private val sessionId: String,
    private val isPlayer1: Boolean,
    private val isFriendly: Boolean = false,
    private val izazovId: String = ""
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PartijaViewModel(sessionId = sessionId, isPlayer1 = isPlayer1, isFriendly = isFriendly, izazovId = izazovId) as T
}
