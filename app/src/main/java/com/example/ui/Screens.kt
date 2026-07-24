package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Chat
import com.example.data.FriendRequest
import com.example.data.Message
import com.example.data.User
import com.example.ui.components.UserAvatar
import com.example.viewmodel.AppScreen
import com.example.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppNavigationContainer(viewModel: AppViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    // Show error snackbars or toasts gracefully
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                slideInHorizontally { width -> width } + fadeIn() togetherWith
                        slideOutHorizontally { width -> -width } + fadeOut()
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                AppScreen.LOGIN -> LoginScreen(viewModel)
                AppScreen.SIGNUP -> SignUpScreen(viewModel)
                AppScreen.DASHBOARD -> DashboardScreen(viewModel)
                AppScreen.CHAT -> ChatScreen(viewModel)
                AppScreen.VERIFY_EMAIL -> VerifyEmailScreen(viewModel)
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var showFirebaseConfig by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF022C22))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.95f)),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Game logo indicator
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.chat_logo_1784623266173),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "ChatOnGame",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif
                )

                Text(
                    text = "Lobby Messaging & Floating Overlays",
                    fontSize = 12.sp,
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Email field
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (Optional if Username entered)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("email_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Username field
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("username_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = if (passwordVisible) "Hide password" else "Show password")
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Forgot Password Clickable Text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Forgot Password?",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { showForgotPasswordDialog = true }
                            .padding(4.dp)
                            .testTag("forgot_password_button")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Login action button
                Button(
                    onClick = { viewModel.login(email, username, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "ENTER LOBBY",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "First time in the arena?",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "CREATE NEW ACCOUNT",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable { viewModel.setScreen(AppScreen.SIGNUP) }
                        .padding(4.dp)
                )
            }
        }
    }

    if (showForgotPasswordDialog) {
        var forgotEmail by remember { mutableStateOf("") }
        var resetCode by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var isCodeSent by remember { mutableStateOf(false) }
        var forgotPassVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            containerColor = Color(0xFF1E293B),
            title = {
                Text(text = "Reset Arena Password", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Enter your registered email address below to reset your password.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = forgotEmail,
                        onValueChange = { forgotEmail = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        enabled = !isCodeSent,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray,
                            disabledTextColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("forgot_email_input")
                    )

                    if (isCodeSent && !com.example.data.FirebaseManager.isFirebaseAvailable) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Simulation: A verification code '1234' was sent to your email. Enter code and new password below.",
                            color = Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = resetCode,
                            onValueChange = { resetCode = it },
                            label = { Text("Verification Code") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("forgot_code_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("New Password (Min 6 chars)") },
                            singleLine = true,
                            visualTransformation = if (forgotPassVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                val iconImage = if (forgotPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { forgotPassVisible = !forgotPassVisible }) {
                                    Icon(imageVector = iconImage, contentDescription = "Toggle Visibility")
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("forgot_new_password_input")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isCodeSent) {
                            if (forgotEmail.trim().isEmpty()) {
                                Toast.makeText(context, "Please enter your email.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.sendPasswordResetEmail(forgotEmail) { result ->
                                result.fold(
                                    onSuccess = {
                                        isCodeSent = true
                                        if (com.example.data.FirebaseManager.isFirebaseAvailable) {
                                            Toast.makeText(context, "Password reset link sent to your email!", Toast.LENGTH_LONG).show()
                                            showForgotPasswordDialog = false
                                        } else {
                                            Toast.makeText(context, "Verification code sent (simulation)!", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    onFailure = { err ->
                                        Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        } else {
                            if (resetCode != "1234") {
                                Toast.makeText(context, "Incorrect verification code. Please use '1234'.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (newPassword.length < 6) {
                                Toast.makeText(context, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.resetPasswordMock(forgotEmail, newPassword) { result ->
                                result.fold(
                                    onSuccess = {
                                        Toast.makeText(context, "Password reset successfully! Please login with your new password.", Toast.LENGTH_LONG).show()
                                        showForgotPasswordDialog = false
                                    },
                                    onFailure = { err ->
                                        Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(text = if (!isCodeSent) "Send" else "Verify & Save", color = Color(0xFF0F172A))
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text(text = "Cancel", color = Color.LightGray)
                }
            }
        )
    }

    if (showFirebaseConfig) {
        var dbUrlInput by remember { mutableStateOf(viewModel.getCustomDatabaseUrl()) }

        AlertDialog(
            onDismissRequest = { showFirebaseConfig = false },
            containerColor = Color(0xFF1E293B),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Firebase Configuration",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Firebase RTDB Config", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "If your Firebase Realtime Database is in Europe, Asia, or has a custom suffix, enter its full URL below to prevent connections from hanging indefinitely.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    OutlinedTextField(
                        value = dbUrlInput,
                        onValueChange = { dbUrlInput = it },
                        label = { Text("Realtime Database URL") },
                        placeholder = { Text("https://your-project-id-default-rtdb.europe-west1.firebasedatabase.app") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("firebase_db_url_input")
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Example URLs:\n• https://chatongame-383b6-default-rtdb.europe-west1.firebasedatabase.app\n• https://chatongame-383b6-default-rtdb.firebaseio.com",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateDatabaseUrl(dbUrlInput)
                        showFirebaseConfig = false
                        Toast.makeText(context, "Database URL updated! Please restart the app or try logging in.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(text = "Save URL", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.updateDatabaseUrl("")
                        showFirebaseConfig = false
                        Toast.makeText(context, "Cleared custom URL. Defaulting to US standard.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(text = "Clear / Default", color = Color.Red)
                }
            }
        )
    }
}

@Composable
fun VerifyEmailScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val emailText = currentUser?.email ?: "your email address"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF022C22))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.95f)),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = "Verification Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Verify Your Account",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "A verification email was sent to:",
                    fontSize = 13.sp,
                    color = Color.LightGray
                )

                Text(
                    text = emailText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Please open the link in the email to verify your address, then click 'I Verified' below to access the arena.",
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        viewModel.checkEmailVerificationStatus { verified ->
                            if (verified) {
                                Toast.makeText(context, "Verification success! Welcome to the Lobby.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Email is still unverified. Please check your inbox and spam folder.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("verify_status_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "I VERIFIED",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.resendVerificationEmail { success ->
                            if (success) {
                                Toast.makeText(context, "Verification email resent successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to resend. Please try again in a few moments.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("resend_verify_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.Gray),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text(
                        text = "RESEND EMAIL",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "LOGOUT / BACK TO LOGIN",
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { viewModel.logout() }
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun SignUpScreen(viewModel: AppViewModel) {
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF022C22))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.95f)),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.chat_logo_1784623266173),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Join ChatOnGame",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Text(
                    text = "Sign up to unlock your permanent Friend ID",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Email field
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_email_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Username field
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Unique Username") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_username_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (Min 6 chars)") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = if (passwordVisible) "Hide password" else "Show password")
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_password_input")
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.signUp(email, username, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("signup_submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "REGISTER & SECURE ID",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Already have an identity?",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "LOGIN TO LOBBY",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable { viewModel.setScreen(AppScreen.LOGIN) }
                        .padding(4.dp)
                )
            }
        }
    }
}

@Composable
fun DashboardScreen(viewModel: AppViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val friendRequests by viewModel.friendRequests.collectAsState()
    val recentChats by viewModel.recentChats.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0 = Chats, 1 = Friends, 2 = Requests
    var showEditUsernameDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmationDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(viewModel.checkOverlayPermission(context)) }
    var isOverlayBannerDismissed by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = viewModel.checkOverlayPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(Color(0xFF0F172A))
                    .statusBarsPadding()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                // Main Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.chat_logo_1784623266173),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ChatOnGame",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }

                    // Settings Icon Button (Gears icon)
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // User profile info banner
                currentUser?.let { user ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                UserAvatar(
                                    avatarUrl = user.avatarUrl,
                                    username = user.username,
                                    size = 46.dp,
                                    showOnlineStatus = true,
                                    isOnline = true,
                                    modifier = Modifier.clickable { showSettingsDialog = true }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = user.username,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Username",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { showEditUsernameDialog = true }
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Friend ID: ${user.friendId}",
                                        color = Color(0xFF10B981),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy ID",
                                        tint = Color.Gray,
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("FriendID", user.friendId)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Friend ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                                            }
                                    )
                                }
                            }
                        }

                        // Dynamic Status Indicator
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (user.isOnline) Color(0xFF10B981) else Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (user.isOnline) "Online" else "Offline",
                                    color = if (user.isOnline) Color(0xFF10B981) else Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                tonalElevation = 4.dp
            ) {
                val totalUnread = recentChats.sumOf { it.unreadCount }
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = {
                        Box {
                            Icon(Icons.Default.Chat, contentDescription = "Chats")
                            if (totalUnread > 0) {
                                Badge(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 6.dp, y = (-4).dp),
                                    containerColor = Color(0xFF10B981)
                                ) {
                                    Text(if (totalUnread > 99) "99+" else "$totalUnread", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    },
                    label = { Text("Chats") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.People, contentDescription = "Friends") },
                    label = { Text("Friends") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = {
                        Box {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Requests")
                            if (friendRequests.isNotEmpty()) {
                                Badge(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp),
                                    containerColor = Color.Red
                                ) {
                                    Text(friendRequests.size.toString(), color = Color.White, fontSize = 9.sp)
                                }
                            }
                        }
                    },
                    label = { Text("Requests") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color.Gray
                    )
                )
            }
        },
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Check & request System Alert Overlay permission banner
            if (!hasOverlayPermission && !isOverlayBannerDismissed) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEC4899).copy(alpha = 0.15f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(Color(0xFFEC4899), Color(0xFFEC4899)))),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        IconButton(
                            onClick = { isOverlayBannerDismissed = true },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss Banner",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = "Overlay Warning", tint = Color(0xFFEC4899))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Floating Chat Bubbles",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Enable overlay permission to chat inside games using floating chat bubbles without leaving your matches!",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.requestOverlayPermission(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Grant Permission", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Tab rendering
            when (activeTab) {
                0 -> ChatsTab(viewModel, recentChats, friends)
                1 -> FriendsTab(viewModel, friends, searchQuery)
                2 -> RequestsTab(viewModel, friendRequests)
            }
        }
    }

    // Edit Username Dialog
    if (showEditUsernameDialog) {
        var newUsernameInput by remember { mutableStateOf(currentUser?.username ?: "") }
        AlertDialog(
            onDismissRequest = { showEditUsernameDialog = false },
            title = { Text("Change Username", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newUsernameInput,
                    onValueChange = { newUsernameInput = it },
                    label = { Text("New Username") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateUsername(newUsernameInput)
                        showEditUsernameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditUsernameDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Settings Dialog
    if (showSettingsDialog) {
        var usernameInput by remember { mutableStateOf(currentUser?.username ?: "") }
        
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "App Settings",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Profile Picture / Avatar Section
                    Text(
                        text = "PROFILE PICTURE / AVATAR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val imagePickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        uri?.let {
                            viewModel.updateAvatarUrl(it.toString()) { success, errorMsg ->
                                if (success) {
                                    Toast.makeText(context, "Profile picture updated from device!", Toast.LENGTH_SHORT).show()
                                } else if (errorMsg != null) {
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                UserAvatar(
                                    avatarUrl = currentUser?.avatarUrl,
                                    username = currentUser?.username ?: "",
                                    size = 56.dp
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentUser?.username ?: "User",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "Custom photo or ready gaming avatar",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Upload button from device
                            OutlinedButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                border = BorderStroke(1.dp, Color(0xFF10B981)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoLibrary,
                                        contentDescription = "Upload Photo",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Upload Photo from Device", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Ready Avatars Category: Football Players
                            Text(
                                text = "FOOTBALL PLAYER AVATARS:",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val footballAvatars = remember {
                                listOf(
                                    Triple("Pro Striker", "res:football_star", Color(0xFF10B981)),
                                    Triple("Lionel Messi", "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?auto=format&fit=crop&w=300&q=80", Color(0xFF3B82F6)),
                                    Triple("Cristiano Ronaldo", "https://images.unsplash.com/photo-1518091043644-c1d4457512c6?auto=format&fit=crop&w=300&q=80", Color(0xFFEF4444)),
                                    Triple("Kylian Mbappé", "https://images.unsplash.com/photo-1574629810360-7efbbe195018?auto=format&fit=crop&w=300&q=80", Color(0xFF8B5CF6)),
                                    Triple("Neymar Jr", "https://images.unsplash.com/photo-1560272564-669525549217?auto=format&fit=crop&w=300&q=80", Color(0xFFF59E0B))
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                footballAvatars.forEach { (name, url, _) ->
                                    val isSelected = currentUser?.avatarUrl == url
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) Color(0xFF10B981) else Color(0xFF334155),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                viewModel.updateAvatarUrl(url)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        UserAvatar(avatarUrl = url, username = name, size = 44.dp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Ready Avatars Category: Chess Pieces
                            Text(
                                text = "CHESS PIECE AVATARS:",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val chessAvatars = remember {
                                listOf(
                                    Triple("Chess King ♔", "res:chess_king", Color(0xFFF59E0B)),
                                    Triple("Chess Queen ♕", "https://images.unsplash.com/photo-1529699211952-734e80c4d42b?auto=format&fit=crop&w=300&q=80", Color(0xFFEC4899)),
                                    Triple("Chess Knight ♞", "https://images.unsplash.com/photo-1586165368502-1bad197a6461?auto=format&fit=crop&w=300&q=80", Color(0xFF8B5CF6)),
                                    Triple("Chess Rook ♜", "https://images.unsplash.com/photo-1563089145-599997674d42?auto=format&fit=crop&w=300&q=80", Color(0xFF3B82F6)),
                                    Triple("Chess Bishop ♗", "https://images.unsplash.com/photo-1528819622765-d6bcf132f793?auto=format&fit=crop&w=300&q=80", Color(0xFF10B981))
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                chessAvatars.forEach { (name, url, _) ->
                                    val isSelected = currentUser?.avatarUrl == url
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) Color(0xFF10B981) else Color(0xFF334155),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                viewModel.updateAvatarUrl(url)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        UserAvatar(avatarUrl = url, username = name, size = 44.dp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Profile section
                    Text(
                        text = "PROFILE USERNAME",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_username_input")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (usernameInput.trim().isNotEmpty()) {
                                    viewModel.updateUsername(usernameInput)
                                    Toast.makeText(context, "Username updated!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Username cannot be empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "ACCOUNT SECURITY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Account Email",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Account Email",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentUser?.email ?: "",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val email = currentUser?.email
                            if (!email.isNullOrEmpty()) {
                                viewModel.sendPasswordResetEmail(email) { result ->
                                    result.fold(
                                        onSuccess = {
                                            Toast.makeText(context, "Password reset email sent to $email!", Toast.LENGTH_LONG).show()
                                        },
                                        onFailure = { err ->
                                            Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            } else {
                                Toast.makeText(context, "No email address found.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_reset_password_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Security, contentDescription = "Reset Password", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Reset / Change Password", fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "OPTIONS & INFORMATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // About Button
                    OutlinedButton(
                        onClick = { showAboutDialog = true },
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("about_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "About", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("About Application", fontSize = 14.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Privacy Policy Button
                    OutlinedButton(
                        onClick = { showPrivacyPolicyDialog = true },
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("privacy_policy_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Security, contentDescription = "Privacy Policy", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Privacy Policy", fontSize = 14.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Logout Button
                    Button(
                        onClick = { showLogoutConfirmationDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_logout_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = Color.White)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Logout", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showSettingsDialog = false }
                ) {
                    Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Logout Confirmation Dialog
    if (showLogoutConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmationDialog = false },
            title = { Text("Logout Warning", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to logout?", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmationDialog = false
                        showSettingsDialog = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Logout", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmationDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // About Application Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About ChatOnGame", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "ChatOnGame is the ultimate overlay chat companion designed specifically for gamers.",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Features:\n• Real-time lobby chat\n• Dynamic overlay chat bubbles\n• Quick companion matches\n• Game presence status",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Version: 1.1.0\nDeveloped with ♥ using Jetpack Compose & Firebase.",
                        color = Color.DarkGray,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Privacy Policy Dialog
    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            title = { Text("Privacy Policy", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Your privacy is extremely important to us. ChatOnGame complies with the standard security principles.",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "1. Data Collection:\nWe collect your username, email, and optionally profile info during sign-up to manage your account details.\n\n" +
                               "2. Real-time Communication:\nAll chats, friend lists, and status updates are encrypted and stored in Google Firebase Realtime Database.\n\n" +
                               "3. Security:\nYour password is secure with Firebase Authentication. We never share any of your private information with third parties.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyPolicyDialog = false }) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

@Composable
fun ChatsTab(viewModel: AppViewModel, chats: List<Chat>, friends: List<User>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "RECENT CONVERSATIONS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (chats.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "No Chats",
                        tint = Color.Gray,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No chats yet. Go to Friends tab to start messaging!",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            val sortedChats = remember(chats) {
                chats.sortedByDescending { it.lastMessageTimestamp }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sortedChats, key = { it.id }) { chat ->
                    val otherUid = chat.participantUids.firstOrNull { it != viewModel.currentUser.value?.uid } ?: ""
                    val otherUser = friends.find { it.uid == otherUid } ?: User(
                        uid = otherUid,
                        username = "Gamer Friend",
                        friendId = "",
                        isOnline = false
                    )

                    val timeString = remember(chat.lastMessageTimestamp) {
                        if (chat.lastMessageTimestamp == 0L) "" else {
                            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                            sdf.format(Date(chat.lastMessageTimestamp))
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.startChatWith(otherUid) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                avatarUrl = otherUser.avatarUrl,
                                username = otherUser.username,
                                size = 44.dp,
                                showOnlineStatus = true,
                                isOnline = otherUser.isOnline
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = otherUser.username,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = timeString,
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = chat.lastMessageText,
                                        color = if (chat.unreadCount > 0) Color.White else Color.LightGray,
                                        fontWeight = if (chat.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (chat.unreadCount > 0) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF25D366))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (chat.unreadCount > 99) "99+" else "${chat.unreadCount}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FriendsTab(viewModel: AppViewModel, friends: List<User>, searchQuery: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(12.dp))

        // Search & Add Friend card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "ADD GAME COMPANION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Username or Friend ID (FC-XXXXXX)", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.sendFriendRequest() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("add_friend_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add", tint = Color(0xFF0F172A))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Group into Online and Offline Friends
        val onlineFriends = friends.filter { it.isOnline }
        val offlineFriends = friends.filter { !it.isOnline }

        Text(
            text = "ONLINE FRIENDS (${onlineFriends.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF10B981),
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (onlineFriends.isEmpty() && offlineFriends.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.chat_logo_1784623266173),
                        contentDescription = "Search Friends",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color.Gray.copy(alpha = 0.5f), CircleShape),
                        alpha = 0.5f,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your friends list is empty.\nEnter a friend's username or Friend ID above to add them!",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Render online
                items(onlineFriends) { friend ->
                    FriendRow(viewModel, friend)
                }

                if (offlineFriends.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "OFFLINE FRIENDS (${offlineFriends.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    items(offlineFriends) { friend ->
                        FriendRow(viewModel, friend)
                    }
                }
            }
        }
    }
}

@Composable
fun FriendRow(viewModel: AppViewModel, friend: User) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.startChatWith(friend.uid) },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(
                    avatarUrl = friend.avatarUrl,
                    username = friend.username,
                    size = 40.dp,
                    showOnlineStatus = true,
                    isOnline = friend.isOnline
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = friend.username,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "ID: ${friend.friendId}",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Remove friend option
            IconButton(
                onClick = { viewModel.removeFriend(friend.uid) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PersonRemove,
                    contentDescription = "Remove Companion",
                    tint = Color.Gray.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun RequestsTab(viewModel: AppViewModel, requests: List<FriendRequest>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "PENDING FRIEND REQUESTS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (requests.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.GroupAdd,
                        contentDescription = "No Requests",
                        tint = Color.Gray,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No pending requests. Game companions seeking to party will appear here!",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(requests) { request ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = request.fromUsername,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Wants to be friends",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }

                            Row {
                                // Accept
                                Button(
                                    onClick = { viewModel.acceptFriendRequest(request) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Reject
                                OutlinedButton(
                                    onClick = { viewModel.rejectFriendRequest(request) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Reject", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(viewModel: AppViewModel) {
    val activeChatId by viewModel.activeChatId.collectAsState()
    val messages by viewModel.activeChatMessages.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val typingMap by viewModel.typingState.collectAsState()

    var textInput by remember { mutableStateOf("") }

    val activeChat = activeChatId ?: ""
    val otherUid = remember(activeChat) {
        activeChat.replace("chat_", "").split("_").firstOrNull { it != currentUser?.uid } ?: ""
    }
    val otherUser = friends.find { it.uid == otherUid } ?: User(
        uid = otherUid,
        username = "Gamer Friend",
        friendId = "",
        isOnline = false
    )

    LaunchedEffect(activeChat) {
        if (activeChat.isNotEmpty()) {
            viewModel.clearUnreadCount(activeChat)
        }
    }

    // Check if other user is typing in this chat
    val isOtherUserTyping = remember(typingMap, activeChat, otherUid) {
        typingMap[otherUid] == activeChat
    }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, activeChat) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(Color(0xFF0F172A))
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.closeChat() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Companion Avatar + Name
                    UserAvatar(
                        avatarUrl = otherUser.avatarUrl,
                        username = otherUser.username,
                        size = 40.dp,
                        showOnlineStatus = true,
                        isOnline = otherUser.isOnline
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = otherUser.username,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (isOtherUserTyping) {
                            Text(
                                text = "typing...",
                                color = Color(0xFF10B981),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = if (otherUser.isOnline) "Online" else "Offline",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(top = 8.dp))
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chat text input box
                OutlinedTextField(
                    value = textInput,
                    onValueChange = {
                        textInput = it
                        // Trigger typing indicator as user inserts characters
                        viewModel.setTypingStatus(it.isNotEmpty())
                    },
                    placeholder = { Text("Send gaming message...", fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    ),
                    maxLines = 3,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Send button
                FloatingActionButton(
                    onClick = {
                        if (textInput.trim().isNotEmpty()) {
                            viewModel.sendMessage(textInput)
                            textInput = ""
                            viewModel.setTypingStatus(false)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color(0xFF0F172A),
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("send_button"),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = false
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(messages) { msg ->
                val isMe = msg.senderId == currentUser?.uid
                val timeString = remember(msg.timestamp) {
                    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                    sdf.format(Date(msg.timestamp))
                }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Column(
                        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            if (!isMe) {
                                Card(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                                ) {
                                    Text(
                                        text = msg.text,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            } else {
                                Card(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF059669))
                                ) {
                                    Text(
                                        text = msg.text,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        // Message Status Indicators & Timestamp
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = timeString,
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                            if (isMe) {
                                Spacer(modifier = Modifier.width(4.dp))
                                val icon = when (msg.status) {
                                    "sent" -> Icons.Default.Check
                                    "delivered" -> Icons.Default.DoneAll
                                    "read" -> Icons.Default.DoneAll
                                    else -> Icons.Default.Check
                                }
                                val color = if (msg.status == "read") Color(0xFF10B981) else Color.Gray
                                Icon(
                                    imageVector = icon,
                                    contentDescription = "Status",
                                    tint = color,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
