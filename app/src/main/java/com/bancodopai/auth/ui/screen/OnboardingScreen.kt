package com.bancodopai.auth.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bancodopai.auth.mvi.action.AuthAction
import com.bancodopai.auth.mvi.state.AuthState
import com.bancodopai.auth.ui.viewmodel.AuthViewModel
import com.bancodopai.domain.model.UserRole
import org.koin.androidx.compose.koinViewModel

@Composable
fun OnboardingScreen(
    viewModel: AuthViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Complete seu cadastro",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Conta Google conectada${if (state.profileEmail.isNotBlank()) " como ${state.profileEmail}" else ""}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = state.profileName,
                onValueChange = {
                    viewModel.onAction(AuthAction.ProfileNameChanged(it))
                },
                label = { Text("Seu nome") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Como você usa o banco?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoleChip(
                    selected = state.profileRole == UserRole.PARENT,
                    label = "Sou pai",
                    onClick = {
                        viewModel.onAction(
                            AuthAction.ProfileRoleSelected(UserRole.PARENT)
                        )
                    }
                )
                RoleChip(
                    selected = state.profileRole == UserRole.CHILD,
                    label = "Sou filho",
                    onClick = {
                        viewModel.onAction(
                            AuthAction.ProfileRoleSelected(UserRole.CHILD)
                        )
                    }
                )
            }

            run {
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = state.profileInviteCode,
                    onValueChange = {
                        viewModel.onAction(
                            AuthAction.ProfileInviteCodeChanged(it)
                        )
                    },
                    label = {
                        Text(
                            if (state.profileRole == UserRole.CHILD) {
                                "Código do seu pai"
                            } else {
                                "Código da família (se já existir)"
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = if (state.profileRole == UserRole.CHILD) {
                        "Peça o código de 6 caracteres ao seu pai, na tela inicial do app dele."
                    } else {
                        "Deixe em branco para criar uma família nova. Se for o outro responsável, use o código do pai."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.profileError?.let { message ->
                Spacer(Modifier.height(16.dp))
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

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = {
                    viewModel.onAction(AuthAction.SubmitProfile)
                },
                enabled = state.canSubmitProfile,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (state.isCreatingProfile) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Criar conta",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = { viewModel.onAction(AuthAction.SignOut) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Usar outra conta")
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Pai e filho usam contas Google diferentes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RoleChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = if (label.contains("pai")) {
                    Icons.Rounded.Badge
                } else {
                    Icons.Rounded.Groups
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}
