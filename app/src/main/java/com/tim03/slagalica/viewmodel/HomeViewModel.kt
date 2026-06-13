package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tim03.slagalica.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val username: String = "",
    val tokens: Int = 0,
    val stars: Int = 0,
    val league: Int = 0,
    val avatarIndex: Int = 0
)

class HomeViewModel(
    private val userRepo: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadUser()
    }

    fun loadUser() {
        viewModelScope.launch {
            runCatching {
                val user = userRepo.getCurrentUser() ?: return@runCatching
                _uiState.value = HomeUiState(
                    username = user.username,
                    tokens = user.tokens,
                    stars = user.stars,
                    league = user.league,
                    avatarIndex = user.avatarIndex
                )
            }
        }
    }
}
