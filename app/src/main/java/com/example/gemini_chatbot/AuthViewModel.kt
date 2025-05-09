package com.example.gemini_chatbot

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "AuthViewModel"
private const val USERS_COLLECTION = "users"

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var googleSignInClient: GoogleSignInClient? = null

    init {
        // Check if user is already signed in
        if (auth.currentUser != null) {
            _authState.value = AuthState.Authenticated
        }
        setupAuthListener()
    }

    private fun setupAuthListener() {
        auth.addAuthStateListener { firebaseAuth ->
            _authState.value = if (firebaseAuth.currentUser != null) {
                AuthState.Authenticated
            } else {
                AuthState.Unauthenticated
            }
        }
    }

    fun initializeGoogleSignIn(webClientId: String) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(getApplication(), gso)
    }

    fun getGoogleSignInIntent(): Intent {
        return googleSignInClient?.signInIntent ?: throw IllegalStateException("GoogleSignInClient not initialized")
    }

    fun handleGoogleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            account?.let { firebaseAuthWithGoogle(it) }
        } catch (e: ApiException) {
            _authState.value = AuthState.Error("Google sign in failed: ${e.message}")
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let { updateUserProfile(it.uid, it.email ?: "", it.displayName ?: "") }
                } else {
                    _authState.value = AuthState.Error("Authentication failed: ${task.exception?.message}")
                }
            }
    }

    private fun updateUserProfile(userId: String, email: String, displayName: String) {
        val userData = hashMapOf(
            "email" to email,
            "displayName" to displayName,
            "lastLogin" to System.currentTimeMillis()
        )

        firestore.collection(USERS_COLLECTION).document(userId)
            .set(userData)
            .addOnSuccessListener {
                _authState.value = AuthState.Authenticated
            }
            .addOnFailureListener { e ->
                _authState.value = AuthState.Error("Failed to update profile: ${e.message}")
            }
    }

    fun signIn(email: String, password: String) {
        _authState.value = AuthState.Loading
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _authState.value = AuthState.Authenticated
                } else {
                    _authState.value = AuthState.Error("Sign in failed: ${task.exception?.message}")
                }
            }
    }

    fun signUp(email: String, password: String) {
        _authState.value = AuthState.Loading
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let { updateUserProfile(it.uid, email, email.split("@").first()) }
                } else {
                    _authState.value = AuthState.Error("Sign up failed: ${task.exception?.message}")
                }
            }
    }

    fun signOut() {
        auth.signOut()
        googleSignInClient?.signOut()
        _authState.value = AuthState.Unauthenticated
    }

    fun resetPassword(email: String) {
        _authState.value = AuthState.Loading
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _authState.value = AuthState.Unauthenticated
                } else {
                    _authState.value = AuthState.Error("Password reset failed: ${task.exception?.message}")
                }
            }
    }

    fun updateDisplayName(newName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser ?: run {
            onError("User not logged in")
            return
        }

        viewModelScope.launch {
            try {
                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(newName)
                    .build()

                user.updateProfile(profileUpdates).await()

                // Update Firestore as well
                firestore.collection(USERS_COLLECTION)
                    .document(user.uid)
                    .update("displayName", newName)
                    .await()

                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating display name", e)
                onError("Failed to update name: ${e.message}")
            }
        }
    }

    fun deleteAccount(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser ?: run {
            onError("User not logged in")
            return
        }

        viewModelScope.launch {
            try {
                // Delete user data from Firestore first
                firestore.collection(USERS_COLLECTION)
                    .document(user.uid)
                    .delete()
                    .await()

                // Delete chats
                val chatCollection = firestore.collection(USERS_COLLECTION)
                    .document(user.uid)
                    .collection("chats")
                    .get()
                    .await()

                for (document in chatCollection.documents) {
                    document.reference.delete().await()
                }

                // Finally delete the user account
                user.delete().await()

                onSuccess()
                // Auth listener will update state
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting account", e)
                _authState.value = AuthState.Error("Error deleting account: ${e.message}")
            }
        }
    }
}