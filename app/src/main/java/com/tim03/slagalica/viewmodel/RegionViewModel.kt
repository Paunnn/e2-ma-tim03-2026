package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.tim03.slagalica.data.repository.RegionLeaderboardEntry
import com.tim03.slagalica.data.repository.RegionRepository
import com.tim03.slagalica.data.repository.RegionStats
import com.tim03.slagalica.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegionUiState(
    val leaderboard: List<RegionStats> = emptyList(),
    val selectedRegion: RegionStats? = null,
    val regionPlayers: List<RegionLeaderboardEntry> = emptyList(),
    val myRegion: String = "",
    val isLoading: Boolean = false,
    val isLoadingPlayers: Boolean = false
)

class RegionViewModel(
    private val regionRepo: RegionRepository = RegionRepository(),
    private val userRepo: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegionUiState())
    val uiState: StateFlow<RegionUiState> = _uiState.asStateFlow()

    private val auth = FirebaseAuth.getInstance()

    init {
        loadLeaderboard()
    }

    fun loadLeaderboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching {
                val myRegion = userRepo.getCurrentUser()?.region ?: ""
                val boards = regionRepo.getRegionalLeaderboard()
                _uiState.value = _uiState.value.copy(
                    leaderboard = boards,
                    myRegion = myRegion,
                    isLoading = false
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun selectRegion(region: RegionStats) {
        _uiState.value = _uiState.value.copy(selectedRegion = region, isLoadingPlayers = true)
        viewModelScope.launch {
            runCatching {
                val players = regionRepo.getRegionPlayers(region.region)
                _uiState.value = _uiState.value.copy(
                    regionPlayers = players,
                    isLoadingPlayers = false
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoadingPlayers = false)
            }
        }
    }

    fun clearSelectedRegion() {
        _uiState.value = _uiState.value.copy(selectedRegion = null, regionPlayers = emptyList())
    }
}
