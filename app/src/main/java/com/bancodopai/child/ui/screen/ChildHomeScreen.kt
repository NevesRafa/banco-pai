package com.bancodopai.child.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bancodopai.child.mvi.action.ChildHomeAction
import com.bancodopai.child.mvi.effect.ChildHomeEffect
import com.bancodopai.child.mvi.state.ChildHomeState
import com.bancodopai.child.ui.viewmodel.ChildHomeViewModel
import com.bancodopai.core.toBrl
import com.bancodopai.core.toShortDateTime
import com.bancodopai.core.ui.components.MoneyField
import com.bancodopai.core.ui.components.TransactionRow
import com.bancodopai.core.ui.theme.ChildTheme
import com.bancodopai.core.ui.theme.NeonGreen
import com.bancodopai.core.ui.theme.PendingAmber
import com.bancodopai.core.ui.theme.TextPrimary
import com.bancodopai.core.ui.theme.TextSecondary
import com.bancodopai.domain.model.Transaction
import com.bancodopai.domain.model.User
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@Composable
fun ChildHomeScreen(
    onSignOut: () -> Unit,
    viewModel: ChildHomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ChildTheme {
        LaunchedEffect(viewModel) {
            viewModel.effect.collect { effect ->
                when (effect) {
                    ChildHomeEffect.WithdrawalRequested ->
                        snackbarHostState.showSnackbar(
                            "Pedido enviado! Seu pai vai aprovar antes do dinheiro sair."
                        )

                    is ChildHomeEffect.ShowMessage ->
                        snackbarHostState.showSnackbar(effect.message)
                }
            }
        }

        LaunchedEffect(state.errorMessage) {
            state.errorMessage?.let { message ->
                snackbarHostState.showSnackbar(message)
                viewModel.onAction(ChildHomeAction.DismissError)
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            if (state.isLoading && state.child == null) {
                LoadingContent(Modifier.padding(innerPadding))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 16.dp,
                        bottom = 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ChildHeader(
                            name = state.child?.name.orEmpty(),
                            onSignOut = onSignOut
                        )
                    }

                    item {
                        BalanceHeroCard(state = state)
                    }

                    item {
                        GoalCard(child = state.child)
                    }

                    item {
                        Button(
                            onClick = {
                                viewModel.onAction(ChildHomeAction.OpenWithdrawSheet)
                            },
                            enabled = state.balanceCents > 0L,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Wallet,
                                contentDescription = null
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                text = "Usar dinheiro",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (state.pendingWithdrawals.isNotEmpty()) {
                        item {
                            SectionTitle("Aguardando aprovação")
                        }
                        items(
                            items = state.pendingWithdrawals,
                            key = { it.id }
                        ) { transaction ->
                            PendingRequestCard(transaction)
                        }
                    }

                    if (state.transactions.isNotEmpty()) {
                        item {
                            SectionTitle("Últimas movimentações")
                        }
                        items(
                            items = state.transactions.take(5),
                            key = { it.id }
                        ) { transaction ->
                            TransactionRow(transaction)
                        }
                    }
                }
            }
        }

        if (state.isWithdrawSheetOpen) {
            WithdrawSheet(
                state = state,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun ChildHeader(
    name: String,
    onSignOut: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Olá, ${name.ifBlank { "campeão" }}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Seu dinheiro, suas escolhas.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        IconButton(onClick = onSignOut) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Logout,
                contentDescription = "Sair",
                tint = TextSecondary
            )
        }
    }
}

@Composable
private fun BalanceHeroCard(state: ChildHomeState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF10251C),
                        Color(0xFF0C1620)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = NeonGreen.copy(alpha = 0.35f),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SALDO ATUAL",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonGreen,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Rounded.AccountBalanceWallet,
                    contentDescription = null,
                    tint = NeonGreen.copy(alpha = 0.8f)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = state.balanceCents.toBrl(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(Modifier.height(8.dp))

            if (state.weeklyInterestCents > 0L) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "+ ${state.weeklyInterestCents.toBrl()} essa semana",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NeonGreen
                    )
                }
            } else {
                Text(
                    text = "Nenhum rendimento essa semana ainda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun GoalCard(child: User?) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Minha meta",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                if (child?.hasGoal == true) {
                    Text(
                        text = "${(child.goalProgress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (child != null && child.hasGoal) {
                Text(
                    text = child.goalName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { child.goalProgress },
                    color = NeonGreen,
                    trackColor = NeonGreen.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                )

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = child.balanceCents.toBrl(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen
                    )
                    Text(
                        text = " de ${child.goalTargetCents.toBrl()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(4.dp))

                val remaining = (child.goalTargetCents - child.balanceCents)
                    .coerceAtLeast(0L)

                Text(
                    text = if (remaining == 0L) {
                        "Meta alcançada! Parabéns!"
                    } else {
                        "Faltam ${remaining.toBrl()} para conquistar."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Converse com seu pai para escolher a primeira meta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PendingRequestCard(transaction: Transaction) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PendingAmber.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = PendingAmber.copy(alpha = 0.14f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = PendingAmber,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.amountCents.toBrl(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = transaction.timestamp.toShortDateTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Pendente",
                style = MaterialTheme.typography.labelSmall,
                color = PendingAmber,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = NeonGreen)
    }
}

@Composable
private fun WithdrawSheet(
    state: ChildHomeState,
    viewModel: ChildHomeViewModel
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.onAction(ChildHomeAction.CloseWithdrawSheet)
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Usar dinheiro",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Seu pai vai receber o pedido e aprovar antes do dinheiro sair.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            MoneyField(
                value = state.withdrawalAmount,
                onValueChange = {
                    viewModel.onAction(ChildHomeAction.WithdrawalAmountChanged(it))
                },
                label = "Valor",
                enabled = !state.isSubmittingWithdrawal,
                isError = state.withdrawalError != null
            )

            OutlinedTextField(
                value = state.withdrawalReason,
                onValueChange = {
                    viewModel.onAction(ChildHomeAction.WithdrawalReasonChanged(it))
                },
                label = { Text("Para que você quer usar?") },
                placeholder = { Text("Ex.: comprar um jogo") },
                enabled = !state.isSubmittingWithdrawal,
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            state.withdrawalError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = "Disponível: ${state.balanceCents.toBrl()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    viewModel.onAction(ChildHomeAction.SubmitWithdrawal)
                },
                enabled = state.canSubmitWithdrawal,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                if (state.isSubmittingWithdrawal) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Enviar pedido",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
