package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    init { load() }

    fun load() {
        viewModelScope.launch {
            runCatching { _missions.value = repo.getTodayMissions() }
        }
    }

    fun completeWinGame() {
        viewModelScope.launch {
            val newlyCompleted = repo.completeWinGame()
            if (newlyCompleted) {
                repo.awardMissionStars()
                _missions.value = repo.getTodayMissions()
                tryCollectBonus()
            }
        }
    }

    fun completeSendMessage() {
        viewModelScope.launch {
            val newlyCompleted = repo.completeSendMessage()
            if (newlyCompleted) {
                repo.awardMissionStars()
                _missions.value = repo.getTodayMissions()
                tryCollectBonus()
            }
        }
    }

    fun completePlayFriendly() {
        viewModelScope.launch {
            val newlyCompleted = repo.completePlayFriendly()
            if (newlyCompleted) {
                repo.awardMissionStars()
                _missions.value = repo.getTodayMissions()
                tryCollectBonus()
            }
        }
    }

    fun completeWinTournament() {
        viewModelScope.launch {
            val newlyCompleted = repo.completeWinTournament()
            if (newlyCompleted) {
                repo.awardMissionStars()
                _missions.value = repo.getTodayMissions()
                tryCollectBonus()
            }
        }
    }

    private suspend fun tryCollectBonus() {
        val collected = repo.collectBonusIfEligible()
        if (collected) _bonusJustCollected.value = true
    }

    fun clearBonusFlag() { _bonusJustCollected.value = false }
}
