package com.example.ui.screens

import android.util.Log
import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.i18n.LocalStrings
import com.example.platform.attemptAutoSignIn
import com.example.platform.onGoogleSignInClicked
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

// =====================================================================
// Email / Password helpers (এই ফাইলের ভেতরেই, তাই বাইরের কিছু লাগবে না)
// =====================================================================

private fun isValidEmail(email: String): Boolean =
    Patterns.EMAIL_ADDRESS.matcher(email).matches()

private fun authErrorMessage(e: Exception): String = when {
    e is FirebaseAuthWeakPasswordException -> "পাসওয়ার্ড খুব দুর্বল। কমপক্ষে ৬ অক্ষর দিন।"
    e is FirebaseAuthUserCollisionException -> "এই ইমেইল দিয়ে আগেই অ্যাকাউন্ট আছে। লগইন করুন, অথবা Google দিয়ে চেষ্টা করুন।"
    e is FirebaseAuthInvalidUserException -> "এই ইমেইলে কোনো অ্যাকাউন্ট নেই, অথবা অ্যাকাউন্টটি বন্ধ।"
    e is FirebaseAuthInvalidCredentialsException -> "ইমেইল বা পাসওয়ার্ড ভুল।"
    e is FirebaseNetworkException -> "ইন্টারনেট সংযোগ নেই। আবার চেষ্টা করুন।"
    e is FirebaseTooManyRequestsException -> "অনেকবার চেষ্টা করা হয়েছে। কিছুক্ষণ পর আবার চেষ্টা করুন।"
    e is FirebaseAuthException && e.errorCode == "ERROR_OPERATION_NOT_ALLOWED" ->
        "Firebase Console-এ Email/Password চালু করা নেই।"
    else -> e.localizedMessage ?: "কিছু একটা সমস্যা হয়েছে।"
}

private suspend fun emailSignUp(email: String, password: String): Result<Unit> {
    val cleanEmail = email.trim()
    if (!isValidEmail(cleanEmail)) {
        return Result.failure(IllegalArgumentException("সঠিক ইমেইল ঠিকানা লিখুন।"))
    }
    if (password.length < 6) {
        return Result.failure(IllegalArgumentException("পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।"))
    }
    return try {
        val result = Firebase.auth.createUserWithEmailAndPassword(cleanEmail, password).await()
        try {
            result.user?.sendEmailVerification()?.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Auth", "Verification email failed: ${e.message}")
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("Auth", "Sign up failed", e)
        Result.failure(Exception(authErrorMessage(e), e))
    }
}

private suspend fun emailSignIn(email: String, password: String): Result<Unit> {
    val cleanEmail = email.trim()
    if (!isValidEmail(cleanEmail)) {
        return Result.failure(IllegalArgumentException("সঠিক ইমেইল ঠিকানা লিখুন।"))
    }
    if (password.isEmpty()) {
        return Result.failure(IllegalArgumentException("পাসওয়ার্ড লিখুন।"))
    }
    return try {
        Firebase.auth.signInWithEmailAndPassword(cleanEmail, password).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("Auth", "Email sign in failed", e)
        Result.failure(Exception(authErrorMessage(e), e))
    }
}

private suspend fun emailPasswordReset(email: String): Result<Unit> {
    val cleanEmail = email.trim()
    if (!isValidEmail(cleanEmail)) {
        return Result.failure(IllegalArgumentException("সঠিক ইমেইল ঠিকানা লিখুন।"))
    }
    return try {
        Firebase.auth.sendPasswordResetEmail(cleanEmail).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("Auth", "Password reset failed", e)
        Result.failure(Exception(authErrorMessage(e), e))
    }
}

// =====================================================================
// Auth Screen
// =====================================================================

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isCheckingAutoSignIn by remember { mutableStateOf(true) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var isEmailLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSignUpMode by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    val isBusy = isGoogleLoading || isEmailLoading || isCheckingAutoSignIn

    val submitEmail: () -> Unit = {
        if (!isBusy) {
            errorMessage = null
            infoMessage = null
            isEmailLoading = true
            val signUp = isSignUpMode
            scope.launch {
                val result = if (signUp) {
                    emailSignUp(email, password)
                } else {
                    emailSignIn(email, password)
                }
                isEmailLoading = false
                result
                    .onSuccess { onAuthSuccess() }
                    .onFailure { e -> errorMessage = e.message ?: "কিছু একটা সমস্যা হয়েছে।" }
            }
        }
    }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingAutoSignIn = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isCheckingAutoSignIn = false
            },
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------- Hero ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(28.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_auth_hero),
                    contentDescription = "Blink25 serene horizon artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RemoveRedEye,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = strings.appName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = strings.tagline,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = strings.authWelcomeSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // ---------- Key Pillars ----------
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Spa,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "25s gentle blink cues & 20-20-20 rest breaks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = strings.authPrivacyNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---------- Messages ----------
            errorMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            infoMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // ---------- Email / Password ----------
            Text(
                text = if (isSignUpMode) "নতুন অ্যাকাউন্ট খুলুন" else "ইমেইল দিয়ে লগইন",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    errorMessage = null
                },
                label = { Text("ইমেইল") },
                singleLine = true,
                enabled = !isBusy,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("email_field")
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                label = {
                    Text(if (isSignUpMode) "পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর)" else "পাসওয়ার্ড")
                },
                singleLine = true,
                enabled = !isBusy,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = if (passwordVisible) "পাসওয়ার্ড লুকান" else "পাসওয়ার্ড দেখুন"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submitEmail() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password_field")
            )

            if (!isSignUpMode) {
                TextButton(
                    onClick = {
                        errorMessage = null
                        infoMessage = null
                        showResetDialog = true
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("forgot_password_button")
                ) {
                    Text("পাসওয়ার্ড ভুলে গেছেন?")
                }
            }

            Button(
                onClick = submitEmail,
                enabled = !isBusy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("email_submit_button")
            ) {
                if (isEmailLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isSignUpMode) "অ্যাকাউন্ট খুলুন" else "লগইন",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            TextButton(
                onClick = {
                    isSignUpMode = !isSignUpMode
                    errorMessage = null
                    infoMessage = null
                },
                enabled = !isBusy,
                modifier = Modifier.testTag("toggle_mode_button")
            ) {
                Text(
                    if (isSignUpMode) "আগেই অ্যাকাউন্ট আছে? লগইন করুন"
                    else "অ্যাকাউন্ট নেই? সাইন আপ করুন"
                )
            }

            // ---------- "অথবা" divider ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Text(
                    text = "অথবা",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }

            // ---------- Google Sign-In ----------
            OutlinedButton(
                onClick = {
                    errorMessage = null
                    infoMessage = null
                    isGoogleLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isGoogleLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isGoogleLoading = false
                            errorMessage = msg
                        },
                        scope = scope,
                        onAuthCancelled = {
                            isGoogleLoading = false
                        }
                    )
                },
                enabled = !isBusy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isGoogleLoading || isCheckingAutoSignIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = strings.signingIn,
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Text(
                        text = strings.signInWithGoogle,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    if (showResetDialog) {
        ForgotPasswordDialog(
            initialEmail = email,
            onDismiss = { showResetDialog = false },
            onResetEmailSent = { msg -> infoMessage = msg }
        )
    }
}

// =====================================================================
// Forgot Password Dialog
// =====================================================================

@Composable
private fun ForgotPasswordDialog(
    initialEmail: String,
    onDismiss: () -> Unit,
    onResetEmailSent: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var resetEmail by remember { mutableStateOf(initialEmail) }
    var isSending by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text("পাসওয়ার্ড রিসেট") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "আপনার অ্যাকাউন্টের ইমেইল লিখুন। আমরা পাসওয়ার্ড রিসেট লিংক পাঠিয়ে দেব।",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = {
                        resetEmail = it
                        errorText = null
                    },
                    label = { Text("ইমেইল") },
                    singleLine = true,
                    enabled = !isSending,
                    isError = errorText != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_email_field")
                )
                errorText?.let { msg ->
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSending,
                onClick = {
                    isSending = true
                    errorText = null
                    scope.launch {
                        val result = emailPasswordReset(resetEmail)
                        isSending = false
                        result
                            .onSuccess {
                                onResetEmailSent(
                                    "পাসওয়ার্ড রিসেট লিংক পাঠানো হয়েছে। ইমেইলের ইনবক্স ও স্প্যাম ফোল্ডার দেখুন।"
                                )
                                onDismiss()
                            }
                            .onFailure { e ->
                                errorText = e.message ?: "রিসেট ইমেইল পাঠানো যায়নি।"
                            }
                    }
                }
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("লিংক পাঠান")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSending,
                onClick = onDismiss
            ) {
                Text("বাতিল")
            }
        }
    )
}
