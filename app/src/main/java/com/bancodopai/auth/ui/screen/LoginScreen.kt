package com.bancodopai.auth.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bancodopai.BuildConfig
import com.bancodopai.auth.google.GoogleSignInCancelledException
import com.bancodopai.auth.google.GoogleSignInClient
import com.bancodopai.auth.mvi.action.AuthAction
import com.bancodopai.auth.mvi.effect.AuthEffect
import com.bancodopai.auth.ui.viewmodel.AuthViewModel
import com.bancodopai.core.ui.theme.NeonGreen
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isGoogleRequesting by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is AuthEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Logo()

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Banco do Pai",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "O banco da sua família",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value = state.email,
                onValueChange = {
                    viewModel.onAction(AuthAction.EmailChanged(it))
                },
                label = { Text("E-mail") },
                leadingIcon = {
                    Icon(Icons.Rounded.Email, contentDescription = null)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = state.password,
                onValueChange = {
                    viewModel.onAction(AuthAction.PasswordChanged(it))
                },
                label = { Text("Senha") },
                leadingIcon = {
                    Icon(Icons.Rounded.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            viewModel.onAction(AuthAction.TogglePasswordVisibility)
                        }
                    ) {
                        Icon(
                            imageVector = if (state.isPasswordVisible) {
                                Icons.Rounded.VisibilityOff
                            } else {
                                Icons.Rounded.Visibility
                            },
                            contentDescription = if (state.isPasswordVisible) {
                                "Ocultar senha"
                            } else {
                                "Mostrar senha"
                            }
                        )
                    }
                },
                visualTransformation = if (state.isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        viewModel.onAction(AuthAction.Submit)
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            state.errorMessage?.let { message ->
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.onAction(AuthAction.Submit)
                },
                enabled = state.canSubmit,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Entrar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (GoogleSignInClient.isConfigured) {
                Spacer(Modifier.height(20.dp))

                Text(
                    text = "ou",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(20.dp))

                OutlinedButton(
                    onClick = {
                        isGoogleRequesting = true
                        scope.launch {
                            val result = GoogleSignInClient.requestIdToken(context)
                            isGoogleRequesting = false
                            result
                                .onSuccess { token ->
                                    viewModel.onAction(AuthAction.GoogleSignIn(token))
                                }
                                .onFailure { error ->
                                    if (error !is GoogleSignInCancelledException) {
                                        viewModel.onAction(
                                            AuthAction.GoogleSignInFailed(
                                                error.message ?: "Falha ao entrar com Google."
                                            )
                                        )
                                    }
                                }
                        }
                    },
                    enabled = !state.isBusy && !isGoogleRequesting,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (state.isGoogleLoading || isGoogleRequesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Entrar com Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = "Um contrato de confiança entre pai e filho.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun Logo() {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        NeonGreen.copy(alpha = 0.24f),
                        Color.Transparent
                    )
                )
            )
            .border(
                width = 1.dp,
                color = NeonGreen.copy(alpha = 0.45f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.AccountBalance,
            contentDescription = null,
            tint = NeonGreen,
            modifier = Modifier.size(44.dp)
        )
    }
}
