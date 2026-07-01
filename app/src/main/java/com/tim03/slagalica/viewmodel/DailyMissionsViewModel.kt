package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.model.DailyMissions
import com.tim03.slagalica.data.repository.DailyMissionsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DailyMissionsViewModel(
    private val repo: DailyMissionsRepository = DailyMissionsRepository()
) : ViewModel() {

    private val _missions = MutableStateFlow(DailyMissions())
    val missions: StateFlow<DailyMissions> = _missions.asStateFlow()

    private val _bonusJustCollected = MutableStateFlow(false)
    val bonusJustCollected: StateFlow<Boolean> = _bonusJustCollected.asStateFlow()

    private var listener: ListenerRegistration? = null

    init { startListening() }

    private fun startListening() {
        viewModelScope.launch {
            // Ensure document exists before attaching listener
            runCatching { repo.getTodayMissions() }
        }
        listener?.remove()
        listener = repo.observeTodayMissions { missions ->
            _missions.value = missions
            // Auto-collect bonus when all 4 missions complete
            if (!missions.bonusCollected && missions.winGame && missions.sendMessage
                && missions.playFriendly && missions.winTournament) {
                viewModelScope.launch {
                    val collected = repo.collectBonusIfEligible()
                    if (collected) _bonusJustCollected.value = true
                }
            }
        }
    }

    // Stars are now awarded inside the repository's completeMission(), so callers
    // only need to call the complete* functions — no separate awardMissionStars needed.
    fun completeWinGame() { viewModelScope.launch { runCatching { repo.completeWinGame() } } }
    fun completeSendMessage() { viewModelScope.launch { runCatching { repo.completeSendMessage() } } }
    fun completePlayFriendly() { viewModelScope.launch { runCatching { repo.completePlayFriendly() } } }
    fun completeWinTournament() { viewModelScope.launch { runCatching { repo.completeWinTournament() } } }

    fun clearBonusFlag() { _bonusJustCollected.value = false }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}
