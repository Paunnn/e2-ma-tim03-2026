package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tim03.slagalica.data.model.SpojniceQuestion
import com.tim03.slagalica.data.repository.SpojniceRepository
import com.tim03.slagalica.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SpojniceUiState(
    val isLoading: Boolean = true,
    val rounds: List<SpojniceQuestion> = emptyList(),
    val error: String? = null
)

class SpojniceViewModel(
    private val roundRepo: SpojniceRepository = SpojniceRepository(),
    private val userRepo: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpojniceUiState())
    val uiState: StateFlow<SpojniceUiState> = _uiState.asStateFlow()

    private val _username = MutableStateFlow("Igrač")
    val username: StateFlow<String> = _username.asStateFlow()

    init {
        loadRounds()
        loadUsername()
    }

    private fun loadUsername() {
        viewModelScope.launch {
            runCatching { userRepo.getCurrentUser()?.username?.also { _username.value = it } }
        }
    }

    private fun loadRounds() {
        viewModelScope.launch {
            try {
                val rounds = roundRepo.getRounds(2)
                _uiState.value = SpojniceUiState(isLoading = false, rounds = rounds)
            } catch (e: Exception) {
                _uiState.value = SpojniceUiState(isLoading = false, error = e.message)
            }
        }
    }

    fun saveResult(connected: Int, totalPairs: Int, won: Boolean) {
        viewModelScope.launch {
            runCatching {
                userRepo.saveSpojniceResult(connected, totalPairs)
                userRepo.saveGameResult(won)
            }
        }
    }
}
