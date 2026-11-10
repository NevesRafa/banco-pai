# Banco do Pai

App Android nativo de educação financeira gamificada para uso em família.
O pai é o "Banco" e o filho é o "Cliente".

## Stack

- Kotlin + Jetpack Compose (Material 3)
- MVI com `StateFlow` + `SharedFlow`
- Koin
- Firebase Auth (e-mail/senha) + Firestore
- Coroutines + Flow

## Status

- [x] Parte 1 — camada de dados, Firebase e Koin
- [x] Parte 2 — ViewModels em MVI
- [ ] Parte 3 — UI Compose (login, home do filho, home do pai)

A `MainActivity` atual é apenas uma tela de confirmação. A UI real entra na Parte 3.

## Configuração do Firebase

O arquivo `app/google-services.json` atual é um **placeholder** e está no `.gitignore`.
Para o app funcionar de verdade:

1. Crie um projeto no [Firebase Console](https://console.firebase.google.com/).
2. Adicione um app Android com o pacote `com.bancodopai`.
3. Baixe o `google-services.json` real e substitua `app/google-services.json`.
4. Ative o provedor **E-mail/senha** em Authentication.
5. Crie o Firestore em modo de produção.
6. Publique as regras:

```bash
firebase deploy --only firestore:rules
```

As regras estão em `firebase/firestore.rules`.

## Dados iniciais

Crie dois usuários em **Authentication** e os documentos correspondentes em `users`.
O ID do documento deve ser o UID do usuário.

Pai:

```json
{
  "name": "Rafael",
  "email": "pai@exemplo.com",
  "role": "PARENT",
  "balanceCents": 0,
  "goalName": "",
  "goalTargetCents": 0,
  "parentId": null
}
```

Filho:

```json
{
  "name": "Filho",
  "email": "filho@exemplo.com",
  "role": "CHILD",
  "balanceCents": 0,
  "goalName": "Comprar jogo",
  "goalTargetCents": 20000,
  "parentId": "UID_DO_PAI"
}
```

Valores monetários são armazenados em **centavos** (`Long`). Ex.: `20000` = R$ 200,00.

## Build

```bash
./gradlew assembleDebug
```

No Windows:

```powershell
.\gradlew.bat assembleDebug
```

## Estrutura

```text
com.bancodopai/
├── auth/
│   ├── mvi/action/AuthAction.kt
│   ├── mvi/effect/AuthEffect.kt
│   ├── mvi/state/AuthState.kt
│   └── ui/viewmodel/AuthViewModel.kt
├── child/
│   ├── mvi/action/ChildHomeAction.kt
│   ├── mvi/effect/ChildHomeEffect.kt
│   ├── mvi/state/ChildHomeState.kt
│   └── ui/viewmodel/ChildHomeViewModel.kt
├── core/
│   ├── FirebaseResult.kt
│   ├── Money.kt
│   ├── mvi/MviViewModel.kt
│   └── ui/theme/Theme.kt
├── data/remote/
│   ├── FirestoreContract.kt
│   ├── FirestoreMappers.kt
│   └── FirebaseFinanceRepository.kt
├── di/
│   └── AppModule.kt
├── domain/model/
│   ├── AppError.kt
│   ├── DepositReason.kt
│   ├── Transaction.kt
│   └── User.kt
├── domain/repository/
│   └── FinanceRepository.kt
└── parent/
    ├── mvi/action/ParentHomeAction.kt
    ├── mvi/effect/ParentHomeEffect.kt
    ├── mvi/state/ParentHomeState.kt
    └── ui/viewmodel/ParentHomeViewModel.kt
```

## MVI da Parte 2

- `MviViewModel<S, A, E>` concentra `state`, `effect`, `updateState` e `emitEffect`.
- `AuthViewModel` observa a sessão do Firebase, faz login/logout e emite navegação por role.
- `ChildHomeViewModel` combina perfil e transações, calcula rendimento dos últimos 7 dias e envia pedidos de saque.
- `ParentHomeViewModel` observa o filho, lista pendências, deposita, aprova e nega saques.
- A UI não altera estado diretamente: envia `Action` e coleta `StateFlow`/`SharedFlow`.

## Observações

- `Google Services` usa o placeholder local; sem o JSON real, o login falha com uma mensagem de Firebase não configurado.
- O `timestamp` retornado por `deposit`/`requestWithdrawal` é aproximado (`Instant.now()`). O valor canônico vem do `serverTimestamp()` pelo Flow.
- A ordenação das transações é feita no cliente para evitar índice composto nesta fase.
