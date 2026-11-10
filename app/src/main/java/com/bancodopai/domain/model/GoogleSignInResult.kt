package com.bancodopai.domain.model

/**
 * Resultado do login com Google.
 * - [Existing]: a conta já tem um documento em `users`.
 * - [NeedsProfile]: autenticou, mas ainda não há cadastro (primeiro acesso).
 */
sealed interface GoogleSignInResult {
    data class Existing(val user: User) : GoogleSignInResult
    data class NeedsProfile(
        val suggestedName: String,
        val email: String
    ) : GoogleSignInResult
}
