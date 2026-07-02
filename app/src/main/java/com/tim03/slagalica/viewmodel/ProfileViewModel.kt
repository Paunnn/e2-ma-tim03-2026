package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tim03.slagalica.data.model.User
import com.tim03.slagalica.data.repository.RegionRepository
import com.tim03.slagalica.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    // 1/2/3 if the user's region finished on the podium in the previous monthly
    // cycle (spec 5e - gold/silver/bronze avatar frame), 0 otherwise.
    val regionPlacementPrevCycle: Int = 0,
    val error: String? = null
)

class ProfileViewModel(
    private val repo: UserRepository = UserRepository(),
    private val regionRepo: RegionRepository = RegionRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadUser()
    }

    fun loadUser() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState(isLoading = true)
            try {
                val user = repo.getCurrentUser()
                val placement = runCatching {
                    regionRepo.getPreviousCyclePlacement(user?.region ?: "")
                }.getOrDefault(0)
                _uiState.value = ProfileUiState(
                    isLoading = false, user = user, regionPlacementPrevCycle = placement
                )
            } catch (e: Exception) {
                _uiState.value = ProfileUiState(isLoading = false, error = e.message)
            }
        }
    }

    fun updateAvatar(index: Int) {
        viewModelScope.launch {
            try {
                repo.updateAvatar(index)
                _uiState.value = _uiState.value.copy(
                    user = _uiState.value.user?.copy(avatarIndex = index)
                )
            } catch (_: Exception) {}
        }
    }

    fun logout() = repo.logout()
}
