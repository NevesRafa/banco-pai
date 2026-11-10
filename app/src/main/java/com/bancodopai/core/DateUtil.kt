package com.bancodopai.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ShortDateTimeFormatter = DateTimeFormatter
    .ofPattern("dd/MM '•' HH:mm")
    .withZone(ZoneId.systemDefault())

fun Instant.toShortDateTime(): String =
    if (this == Instant.EPOCH) {
        "agora"
    } else {
        ShortDateTimeFormatter.format(this)
    }
