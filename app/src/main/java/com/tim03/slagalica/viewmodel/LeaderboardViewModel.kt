package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.tim03.slagalica.data.model.LeaderboardEntry
import com.tim03.slagalica.data.repository.LeaderboardRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LeaderboardUiState(
    val weeklyEntries: List<LeaderboardEntry> = emptyList(),
    val monthlyEntries: List<LeaderboardEntry> = emptyList(),
    val weekRange: Pair<String, String> = Pair("", ""),
    val monthRange: Pair<String, String> = Pair("", ""),
    val myUid: String = "",
    val isLoading: Boolean = false,
    val selectedTab: Int = 0
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
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(120_000) // 2 minutes
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
}
