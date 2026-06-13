package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tim03.slagalica.data.model.KoZnaZnaQuestion
import com.tim03.slagalica.data.repository.KoZnaZnaRepository
import com.tim03.slagalica.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class KoZnaZnaUiState(
    val isLoading: Boolean = true,
    val questions: List<KoZnaZnaQuestion> = emptyList(),
    val error: String? = null
)

class KoZnaZnaViewModel(
    private val questionRepo: KoZnaZnaRepository = KoZnaZnaRepository(),
    private val userRepo: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(KoZnaZnaUiState())
    val uiState: StateFlow<KoZnaZnaUiState> = _uiState.asStateFlow()

    private val _username = MutableStateFlow("Igrač")
    val username: StateFlow<String> = _username.asStateFlow()

    init {
        loadQuestions()
        loadUsername()
    }

    private fun loadUsername() {
        viewModelScope.launch {
            runCatching { userRepo.getCurrentUser()?.username?.also { _username.value = it } }
        }
    }

    private fun loadQuestions() {
        viewModelScope.launch {
            try {
                val questions = questionRepo.getQuestions(5)
                _uiState.value = KoZnaZnaUiState(isLoading = false, questions = questions)
            } catch (e: Exception) {
                _uiState.value = KoZnaZnaUiState(isLoading = false, error = e.message)
            }
        }
    }

    fun saveResult(correct: Int, incorrect: Int, won: Boolean) {
        viewModelScope.launch {
            runCatching {
                userRepo.saveKoZnaZnaResult(correct, incorrect)
                userRepo.saveGameResult(won)
            }
        }
    }
}
