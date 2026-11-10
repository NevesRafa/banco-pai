package com.bancodopai.parent.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bancodopai.core.toBrl
import com.bancodopai.core.toShortDateTime
import com.bancodopai.core.ui.components.MoneyField
import com.bancodopai.core.ui.components.TransactionRow
import com.bancodopai.core.ui.theme.CreditGreen
import com.bancodopai.core.ui.theme.ParentTheme
import com.bancodopai.core.ui.theme.PendingAmber
import com.bancodopai.core.ui.theme.TextPrimary
import com.bancodopai.core.ui.theme.TextSecondary
import com.bancodopai.domain.model.DepositReason
import com.bancodopai.domain.model.Transaction
import com.bancodopai.domain.model.User
import com.bancodopai.parent.mvi.action.ParentHomeAction
import com.bancodopai.parent.mvi.effect.ParentHomeEffect
import com.bancodopai.parent.mvi.state.ParentHomeState
import com.bancodopai.parent.ui.viewmodel.ParentHomeViewModel
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@Composable
fun ParentHomeScreen(
    onSignOut: () -> Unit,
    viewModel: ParentHomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val pendingIds = state.pendingWithdrawals.map { it.id }.toSet()
    val history = state.transactions.filterNot { it.id in pendingIds }.take(6)

    ParentTheme {
        LaunchedEffect(viewModel) {
            viewModel.effect.collect { effect ->
                when (effect) {
                    ParentHomeEffect.DepositCompleted ->
                        snackbarHostState.showSnackbar("Depósito realizado com sucesso.")

                    ParentHomeEffect.WithdrawalApproved ->
                        snackbarHostState.showSnackbar("Saque aprovado.")

                    ParentHomeEffect.WithdrawalDenied ->
                        snackbarHostState.showSnackbar("Saque negado.")

                    is ParentHomeEffect.ShowMessage ->
                        snackbarHostState.showSnackbar(effect.message)
                }
            }
        }

        LaunchedEffect(state.errorMessage) {
            state.errorMessage?.let { message ->
                snackbarHostState.showSnackbar(message)
                viewModel.onAction(ParentHomeAction.DismissError)
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Torre de Controle",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = onSignOut) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Sair",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = TextPrimary
                    )
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.onAction(ParentHomeAction.OpenDepositSheet)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null
                        )
                    },
                    text = { Text("Depositar") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp)
                )
            },
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
                        top = 8.dp,
                        bottom = 110.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ParentGreeting(parent = state.parent)
                    }

                    item {
                        ParentSummaryCard(
                            child = state.child,
                            balanceCents = state.childBalanceCents
                        )
                    }

                    item {
                        SectionHeader(
                            title = "Pendências",
                            count = state.pendingWithdrawals.size
                        )
                    }

                    if (state.pendingWithdrawals.isEmpty()) {
                        item {
                            EmptyPendingCard()
                        }
                    } else {
                        items(
                            items = state.pendingWithdrawals,
                            key = { "pending_${it.id}" }
                        ) { transaction ->
                            PendingWithdrawalCard(
                                transaction = transaction,
                                isProcessing = state.processingTransactionId == transaction.id,
                                onApprove = {
                                    viewModel.onAction(
                                        ParentHomeAction.ApproveWithdrawal(transaction.id)
                                    )
                                },
                                onDeny = {
                                    viewModel.onAction(
                                        ParentHomeAction.DenyWithdrawal(transaction.id)
                                    )
                                }
                            )
                        }
                    }

                    if (history.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Histórico recente")
                        }
                        items(
                            items = history,
                            key = { "transaction_${it.id}" }
                        ) { transaction ->
                            TransactionRow(transaction)
                        }
                    }
                }
            }
        }

        if (state.isDepositSheetOpen) {
            DepositSheet(
                state = state,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun ParentGreeting(parent: User?) {
    Column {
        Text(
            text = "Olá, ${parent?.name?.ifBlank { "pai" } ?: "pai"}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Acompanhe e aprove as decisões do seu filho.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun ParentSummaryCard(
    child: User?,
    balanceCents: Long
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(22.dp)) {
            Text(
                text = "PATRIMÔNIO ADMINISTRADO",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = balanceCents.toBrl(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = child?.name?.ifBlank { "Filho não vinculado" }
                        ?: "Filho não vinculado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (child != null && child.hasGoal) {
                Spacer(Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { child.goalProgress },
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Meta: ${child.goalName} • " +
                        "${(child.goalProgress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int = 0
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        if (count > 0) {
            Spacer(Modifier.size(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyPendingCard() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = CreditGreen.copy(alpha = 0.14f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = CreditGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.size(12.dp))

            Column {
                Text(
                    text = "Tudo em dia",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Nenhum pedido de saque aguardando aprovação.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PendingWithdrawalCard(
    transaction: Transaction,
    isProcessing: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                Spacer(Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.amountCents.toBrl(),
                        style = MaterialTheme.typography.titleLarge,
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
                    text = "PENDENTE",
                    style = MaterialTheme.typography.labelSmall,
                    color = PendingAmber,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDeny,
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Negar")
                }

                Button(
                    onClick = onApprove,
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Aprovar")
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DepositSheet(
    state: ParentHomeState,
    viewModel: ParentHomeViewModel
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val depositCents = state.depositCents

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.onAction(ParentHomeAction.CloseDepositSheet)
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
                text = "Depositar dinheiro",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "O valor entra imediatamente no saldo de " +
                    "${state.child?.name ?: "seu filho"}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            MoneyField(
                value = state.depositAmount,
                onValueChange = {
                    viewModel.onAction(ParentHomeAction.DepositAmountChanged(it))
                },
                label = "Valor",
                enabled = !state.isSubmittingDeposit,
                isError = state.depositError != null
            )

            Text(
                text = "Motivo",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DepositReason.entries.forEach { reason ->
                    FilterChip(
                        selected = state.depositReason == reason,
                        onClick = {
                            viewModel.onAction(
                                ParentHomeAction.DepositReasonSelected(reason)
                            )
                        },
                        label = { Text(reason.label) },
                        leadingIcon = if (state.depositReason == reason) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            null
                        },
                        shape = RoundedCornerShape(50)
                    )
                }
            }

            if (state.depositReason == DepositReason.OUTROS) {
                OutlinedTextField(
                    value = state.depositNote,
                    onValueChange = {
                        viewModel.onAction(ParentHomeAction.DepositNoteChanged(it))
                    },
                    label = { Text("Descreva o motivo") },
                    enabled = !state.isSubmittingDeposit,
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            state.depositError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    viewModel.onAction(ParentHomeAction.SubmitDeposit)
                },
                enabled = state.canSubmitDeposit,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                if (state.isSubmittingDeposit) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (depositCents != null) {
                            "Depositar ${depositCents.toBrl()}"
                        } else {
                            "Depositar"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
