package com.example.gemini_chatbot

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "ChatViewModel"

class ChatViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    // Gemini API key - should be stored more securely in production
    private val apiKey = BuildConfig.GEMINI_API_KEY
    private lateinit var generativeModel: GenerativeModel

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        initializeGenerativeModel()
    }

    private fun initializeGenerativeModel() {
        try {
            generativeModel = GenerativeModel(
                modelName = "gemini-1.0-pro",
                apiKey = apiKey
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Gemini model", e)
            _uiState.value = ChatUiState.Error("Failed to initialize AI: ${e.message}")
        }
    }

    fun sendMessage(messageText: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _uiState.value = ChatUiState.Loading

                // Create user message
                val userMessage = ChatMessage(
                    text = messageText,
                    isUser = true,
                    status = MessageStatus.SENDING
                )

                // Add user message to the list
                _messages.value = _messages.value + userMessage

                // Get AI response
                val response = generativeModel.generateContent(messageText)
                val aiResponseText = response.text?.trim() ?: "Sorry, I couldn't generate a response."

                // Create AI message
                val aiMessage = ChatMessage(
                    text = aiResponseText,
                    isUser = false
                )

                // Add AI message to the list
                _messages.value = _messages.value + aiMessage
                _uiState.value = ChatUiState.Success(aiResponseText)

            } catch (e: Exception) {
                Log.e(TAG, "Error getting AI response", e)
                _uiState.value = ChatUiState.Error("Error: ${e.message}")

                // Create error message
                val errorMessage = ChatMessage(
                    text = "Sorry, I encountered an error: ${e.message}",
                    isUser = false,
                    status = MessageStatus.ERROR
                )

                // Add error message to the list
                _messages.value = _messages.value + errorMessage

            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retryLastUserMessage() {
        val lastUserMessage = _messages.value.lastOrNull { it.isUser }
        lastUserMessage?.let {
            sendMessage(it.text)
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        _uiState.value = ChatUiState.Idle
    }
}