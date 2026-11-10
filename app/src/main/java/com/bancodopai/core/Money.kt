package com.bancodopai.core

import com.bancodopai.domain.model.AppError
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

private val BrlLocale = Locale("pt", "BR")

/**
 * Converte texto digitado em centavos.
 * Aceita "12,50", "12.50", "1.234,56" e "12".
 */
fun String.toCentsOrNull(): Long? {
    val cleaned = trim()
        .replace("R$", "")
        .replace(" ", "")

    if (cleaned.isBlank()) return null

    val normalized = when {
        cleaned.contains(',') ->
            cleaned.replace(".", "").replace(',', '.')

        cleaned.contains('.') &&
            cleaned.substringAfterLast('.').length <= 2 -> cleaned

        else -> cleaned.replace(".", "")
    }

    val value = normalized.toBigDecimalOrNull() ?: return null
    if (value < BigDecimal.ZERO) return null

    return value
        .setScale(2, RoundingMode.HALF_UP)
        .movePointRight(2)
        .toLong()
}

fun Long.toBrl(): String =
    NumberFormat.getCurrencyInstance(BrlLocale)
        .format(BigDecimal.valueOf(this, 2))

internal fun Throwable.userMessage(): String =
    (this as? AppError)?.message
        ?: message?.takeIf { it.isNotBlank() }
        ?: "Ocorreu um erro inesperado."
