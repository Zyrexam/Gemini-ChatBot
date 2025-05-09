package com.example.gemini_chatbot

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gemini_chatbot.ui.theme.GeminiChatBotTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set window flags for better IME handling
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Initialize Google Sign In
        authViewModel.initializeGoogleSignIn(getString(R.string.default_web_client_id))

        // Setup Google Sign In launcher
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                authViewModel.handleGoogleSignInResult(result.data)
            }
        }

        setContent {
            GeminiChatBotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onGoogleSignInClick = {
                            try {
                                val signInIntent = authViewModel.getGoogleSignInIntent()
                                googleSignInLauncher.launch(signInIntent)
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error launching Google Sign-In", e)
                                Toast.makeText(this, "Error launching Google Sign-In", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    override fun onPause() {
        super.onPause()
        window.decorView.clearFocus()
    }

    @Composable
    fun MainScreen(
        authViewModel: AuthViewModel = viewModel(),
        chatViewModel: ChatViewModel = viewModel(),
        onGoogleSignInClick: () -> Unit
    ) {
        val authState by authViewModel.authState.collectAsState()

        when (authState) {
            is AuthState.Authenticated -> {
                ChatScreen(
                    chatViewModel = chatViewModel,
                    onSignOut = { authViewModel.signOut() }
                )
            }
            is AuthState.Unauthenticated -> {
                AuthScreen(
                    onAuthSuccess = { /* Auth listener will handle state update */ },
                    onGoogleSignInClick = onGoogleSignInClick
                )
            }
            is AuthState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is AuthState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (authState as AuthState.Error).message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ChatScreen(
        chatViewModel: ChatViewModel,
        onSignOut: () -> Unit
    ) {
        val messages by chatViewModel.messages.collectAsState()
        val isLoading by chatViewModel.isLoading.collectAsState()
        val uiState by chatViewModel.uiState.collectAsState()
        var messageText by remember { mutableStateOf("") }
        val listState = rememberLazyListState()

        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Gemini Chat") },
                    actions = {
                        IconButton(onClick = onSignOut) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    state = listState
                ) {
                    items(messages) { message ->
                        ChatMessageItem(message = message)
                    }
                    if (isLoading) {
                        item {
                            TypingIndicator()
                        }
                    }
                }

                if (messages.isEmpty()) {
                    EmptyStateMessage()
                }

                SuggestionChips(
                    onSuggestionClick = { suggestion ->
                        messageText = suggestion
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        placeholder = { Text("Type a message...") },
                        maxLines = 5
                    )

                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                chatViewModel.sendMessage(messageText)
                                messageText = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ChatMessageItem(message: ChatMessage) {
        val alignment = if (message.isUser) Alignment.End else Alignment.Start
        val backgroundColor = if (message.isUser) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondary
        }
        val contentColor = if (message.isUser) {
            Color.White
        } else {
            MaterialTheme.colorScheme.onSecondary
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = alignment
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (message.isUser) 16.dp else 4.dp,
                            bottomEnd = if (message.isUser) 4.dp else 16.dp
                        )
                    )
                    .background(backgroundColor)
                    .padding(12.dp)
            ) {
                Text(
                    text = message.text,
                    color = contentColor,
                    fontSize = 16.sp
                )
            }
            Text(
                text = formatTimestamp(message.timestamp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }

    @Composable
    fun TypingIndicator() {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.secondary)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.onSecondary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Gemini is typing...",
                color = MaterialTheme.colorScheme.onSecondary,
                fontSize = 14.sp
            )
        }
    }

    @Composable
    fun EmptyStateMessage() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.message),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Start a conversation with Gemini",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ask me anything! I'm here to help.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SuggestionChips(
        onSuggestionClick: (String) -> Unit
    ) {
        val suggestions = listOf(
            "What can you do?",
            "Tell me a joke",
            "Help me with coding",
            "Explain a concept"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = { onSuggestionClick(suggestion) },
                    label = { Text(suggestion) }
                )
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}



