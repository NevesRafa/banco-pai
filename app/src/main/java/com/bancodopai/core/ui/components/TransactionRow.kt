package com.bancodopai.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bancodopai.core.toBrl
import com.bancodopai.core.toShortDateTime
import com.bancodopai.core.ui.theme.CreditGreen
import com.bancodopai.core.ui.theme.PendingAmber
import com.bancodopai.domain.model.Transaction
import com.bancodopai.domain.model.TransactionStatus
import com.bancodopai.domain.model.TransactionType

@Composable
fun TransactionRow(
    transaction: Transaction,
    modifier: Modifier = Modifier
) {
    val tint = when (transaction.type) {
        TransactionType.DEPOSIT -> CreditGreen
        TransactionType.WITHDRAW -> MaterialTheme.colorScheme.error
        TransactionType.INTEREST -> MaterialTheme.colorScheme.secondary
    }

    val icon = when (transaction.type) {
        TransactionType.DEPOSIT -> Icons.Rounded.ArrowDownward
        TransactionType.WITHDRAW -> Icons.Rounded.ArrowUpward
        TransactionType.INTEREST -> Icons.AutoMirrored.Rounded.TrendingUp
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = tint.copy(alpha = 0.14f),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = transaction.description.ifBlank {
                    transaction.type.label()
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${transaction.type.label()} • ${transaction.timestamp.toShortDateTime()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (transaction.isCredit) "+ " else "- ") +
                    transaction.amountCents.toBrl(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (transaction.isCredit) {
                    CreditGreen
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            StatusLabel(transaction.status)
        }
    }
}

@Composable
private fun StatusLabel(status: TransactionStatus) {
    val color: Color
    val label: String

    when (status) {
        TransactionStatus.PENDING -> {
            label = "Pendente"
            color = PendingAmber
        }

        TransactionStatus.APPROVED -> {
            label = "Aprovado"
            color = CreditGreen
        }

        TransactionStatus.DENIED -> {
            label = "Negado"
            color = MaterialTheme.colorScheme.error
        }
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

private fun TransactionType.label(): String = when (this) {
    TransactionType.DEPOSIT -> "Depósito"
    TransactionType.WITHDRAW -> "Saque"
    TransactionType.INTEREST -> "Rendimento"
}
