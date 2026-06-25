package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.model.ChatMessage
import com.tim03.slagalica.data.repository.ChatRepository
import com.tim03.slagalica.data.repository.DailyMissionsRepository
import com.tim03.slagalica.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepo: ChatRepository = ChatRepository(),
    private val userRepo: UserRepository = UserRepository(),
    private val missionRepo: DailyMissionsRepository = DailyMissionsRepository()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _region = MutableStateFlow("")
    val region: StateFlow<String> = _region.asStateFlow()

    private val _myUid = MutableStateFlow("")
    val myUid: StateFlow<String> = _myUid.asStateFlow()

    private var listener: ListenerRegistration? = null

    init {
        viewModelScope.launch {
            runCatching {
                val user = userRepo.getCurrentUser() ?: return@runCatching
                _myUid.value = user.uid
                _region.value = user.region.ifEmpty { "Srbija" }
                startListening(user.region.ifEmpty { "Srbija" })
            }
        }
    }

    private fun startListening(region: String) {
        listener?.remove()
        listener = chatRepo.listenToMessages(region) { msgs ->
            _messages.value = msgs
        }
    }

    fun onInputChange(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return
        val uid = auth.currentUser?.uid ?: return
        val region = _region.value
        _inputText.value = ""
        viewModelScope.launch {
            runCatching {
                val user = userRepo.getCurrentUser() ?: return@runCatching
                chatRepo.sendMessage(region, uid, user.username, text)
                missionRepo.completeSendMessage()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}
