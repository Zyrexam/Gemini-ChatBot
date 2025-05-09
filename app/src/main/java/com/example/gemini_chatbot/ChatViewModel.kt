package com.example.gemini_chatbot

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

private const val TAG = "ChatViewModel"
private const val USERS_COLLECTION = "users"
private const val CHATS_COLLECTION = "chats"
private const val MESSAGES_COLLECTION = "messages"

class ChatViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Gemini API key - should be stored more securely in production
    private val apiKey = BuildConfig.GEMINI_API_KEY
    private lateinit var generativeModel: GenerativeModel

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentChatId: String? = null

    init {
        initializeGenerativeModel()
        loadMessages()
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

    private fun loadMessages() {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                // Get or create chat
                currentChatId = getCurrentOrCreateChat(userId)

                // Load messages for this chat
                firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(CHATS_COLLECTION)
                    .document(currentChatId!!)
                    .collection(MESSAGES_COLLECTION)
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Log.w(TAG, "Listen failed.", e)
                            return@addSnapshotListener
                        }

                        if (snapshot != null) {
                            val messagesList = snapshot.documents.mapNotNull { doc ->
                                doc.toObject(ChatMessage::class.java)
                            }
                            _messages.value = messagesList
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages", e)
                _uiState.value = ChatUiState.Error("Failed to load messages: ${e.message}")
            }
        }
    }

    private suspend fun getCurrentOrCreateChat(userId: String): String {
        // Check if user has any chats
        val chatsRef = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(CHATS_COLLECTION)

        val chatsSnapshot = chatsRef.limit(1).get().await()

        // If no chats exist, create a new one
        if (chatsSnapshot.isEmpty) {
            val newChatId = UUID.randomUUID().toString()
            val chatData = hashMapOf(
                "title" to "New Chat",
                "createdAt" to System.currentTimeMillis(),
                "lastMessage" to "No messages yet"
            )

            chatsRef.document(newChatId).set(chatData).await()
            return newChatId
        }

        // Otherwise return the first chat
        return chatsSnapshot.documents[0].id
    }

    fun sendMessage(messageText: String) {
        val userId = auth.currentUser?.uid ?: return
        if (currentChatId == null) return

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

                // Save to Firestore
                val messageRef = firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(CHATS_COLLECTION)
                    .document(currentChatId!!)
                    .collection(MESSAGES_COLLECTION)
                    .document(userMessage.id)

                messageRef.set(userMessage).await()

                // Update chat's last message
                firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(CHATS_COLLECTION)
                    .document(currentChatId!!)
                    .update("lastMessage", messageText)

                // Get AI response
                val response = generativeModel.generateContent(messageText)
                val aiResponseText = response.text?.trim() ?: "Sorry, I couldn't generate a response."

                // Create AI message
                val aiMessage = ChatMessage(
                    text = aiResponseText,
                    isUser = false
                )

                // Save AI message to Firestore
                firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(CHATS_COLLECTION)
                    .document(currentChatId!!)
                    .collection(MESSAGES_COLLECTION)
                    .document(aiMessage.id)
                    .set(aiMessage)
                    .await()

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

                // Save error message to Firestore
                firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(CHATS_COLLECTION)
                    .document(currentChatId!!)
                    .collection(MESSAGES_COLLECTION)
                    .document(errorMessage.id)
                    .set(errorMessage)
                    .await()

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
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _uiState.value = ChatUiState.Loading

                val chatRef = firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(CHATS_COLLECTION)
                    .document(currentChatId!!)
                    .collection(MESSAGES_COLLECTION)

                val messages = chatRef.get().await()
                for (message in messages.documents) {
                    message.reference.delete().await()
                }

                _messages.value = emptyList()
                _uiState.value = ChatUiState.Success("Chat cleared successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Error clearing chat", e)
                _uiState.value = ChatUiState.Error("Failed to clear chat: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
}