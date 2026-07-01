package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.tim03.slagalica.data.model.LeaderboardEntry
import com.tim03.slagalica.data.repository.LeaderboardRepository
import com.tim03.slagalica.data.repository.RewardResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PendingReward(val tokens: Int, val rank: Int, val isMonthly: Boolean)

data class LeaderboardUiState(
    val weeklyEntries: List<LeaderboardEntry> = emptyList(),
    val monthlyEntries: List<LeaderboardEntry> = emptyList(),
    val weekRange: Pair<String, String> = Pair("", ""),
    val monthRange: Pair<String, String> = Pair("", ""),
    val myUid: String = "",
    val isLoading: Boolean = false,
    val selectedTab: Int = 0,
    val pendingReward: PendingReward? = null
)

class LeaderboardViewModel(
    private val repo: LeaderboardRepository = LeaderboardRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            myUid = FirebaseAuth.getInstance().currentUser?.uid ?: "",
            weekRange = repo.getCurrentWeekDates(),
            monthRange = repo.getCurrentMonthDates()
        )
        checkCycleRewards()
        startAutoRefresh()
    }

    private fun checkCycleRewards() {
        viewModelScope.launch {
            runCatching {
                val weeklyResult = repo.checkAndDistributeWeeklyRewards()
                if (weeklyResult is RewardResult.Earned) {
                    _uiState.value = _uiState.value.copy(
                        pendingReward = PendingReward(weeklyResult.tokens, weeklyResult.rank, false)
                    )
                    return@runCatching
                }
                val monthlyResult = repo.checkAndDistributeMonthlyRewards()
                if (monthlyResult is RewardResult.Earned) {
                    _uiState.value = _uiState.value.copy(
                        pendingReward = PendingReward(monthlyResult.tokens, monthlyResult.rank, true)
                    )
                }
            }
        }
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(120_000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching {
                val weekly = repo.getWeeklyLeaderboard()
                val monthly = repo.getMonthlyLeaderboard()
                _uiState.value = _uiState.value.copy(
                    weeklyEntries = weekly,
                    monthlyEntries = monthly,
                    isLoading = false
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun selectTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun dismissReward() {
        val reward = _uiState.value.pendingReward ?: return
        viewModelScope.launch {
            runCatching {
                if (reward.isMonthly) repo.clearPendingMonthlyReward()
                else repo.clearPendingWeeklyReward()
            }
            _uiState.value = _uiState.value.copy(pendingReward = null)
        }
    }
}
