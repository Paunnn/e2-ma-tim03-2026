package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tim03.slagalica.data.repository.UserRepository
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
    val isComplete: Boolean = false,
    val resultSaved: Boolean = false
)

class PartijaViewModel(
    private val userRepo: UserRepository = UserRepository()
) : ViewModel() {

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

    private val _uiState = MutableStateFlow(PartijaUiState())
    val uiState: StateFlow<PartijaUiState> = _uiState.asStateFlow()

    fun gameCompleted(myScore: Int, oppScore: Int) {
        val s = _uiState.value
        val newMy = s.myTotal + myScore
        val newOpp = s.oppTotal + oppScore
        val nextIndex = s.currentGameIndex + 1
        _uiState.value = if (nextIndex >= GAME_ORDER.size) {
            s.copy(myTotal = newMy, oppTotal = newOpp, isComplete = true)
        } else {
            s.copy(currentGameIndex = nextIndex, myTotal = newMy, oppTotal = newOpp)
        }
    }

    fun saveResult() {
        val s = _uiState.value
        if (s.resultSaved) return
        _uiState.value = s.copy(resultSaved = true)
        val won = s.myTotal > s.oppTotal
        val drawn = s.myTotal == s.oppTotal
        viewModelScope.launch {
            runCatching { userRepo.savePartijaResult(won, drawn) }
        }
    }
}
