package com.bancodopai.domain.model

sealed class AppError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    data object Unauthenticated : AppError("Sua sessão expirou. Entre novamente.")

    data object PermissionDenied : AppError("Você não tem permissão para esta ação.")

    data object NotFound : AppError("Não encontramos os dados solicitados.")

    class Validation(message: String) : AppError(message)

    class InsufficientFunds(val availableCents: Long) :
        AppError("Saldo insuficiente para este pedido.")

    class Network(cause: Throwable) :
        AppError("Falha de conexão. Verifique sua internet.", cause)

    class Unknown(cause: Throwable) :
        AppError("Ocorreu um erro inesperado. Tente novamente.", cause)
}
