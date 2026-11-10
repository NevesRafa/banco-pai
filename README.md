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
- [x] Parte 3 — UI Compose (login, home do filho, home do pai)

O app abre na tela de login e, após autenticar, navega para a home do filho ou a torre de controle do pai conforme o `role` do usuário.

## Configuração do Firebase

Projeto Firebase: **`bancopai`**
Firestore: banco `(default)` em `southamerica-east1` (São Paulo).

O `google-services.json` real já está configurado em `app/google-services.json`, mas é **ignorado pelo Git** para não versionar chaves. Para outro ambiente:

1. Crie um projeto no [Firebase Console](https://console.firebase.google.com/).
2. Adicione um app Android com o pacote `com.bancodopai`.
3. Baixe o `google-services.json` real e coloque em `app/google-services.json`.
4. Ative o provedor **E-mail/senha** em Authentication.
5. Crie o Firestore em modo de produção.
6. Publique as regras:

```bash
firebase deploy --only firestore:rules --project bancopai
```

As regras estão em `firebase/firestore.rules` e o projeto padrão em `.firebaserc`.

## Dados iniciais

O projeto `bancopai` já vem com duas contas de teste e documentos semeados no Firestore:

| Papel | E-mail | Senha |
|---|---|---|
| Pai | `pai@bancopai.app` | `BancoPai123!` |
| Filho | `filho@bancopai.app` | `BancoPai123!` |

O filho começa com R$ 50,00, meta de R$ 200,00 e um pedido de saque pendente de R$ 20,00.

Para recriar manualmente, crie os usuários em **Authentication** e os documentos correspondentes em `users`.
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
├── MainActivity.kt
├── auth/
│   ├── mvi/action/AuthAction.kt
│   ├── mvi/effect/AuthEffect.kt
│   ├── mvi/state/AuthState.kt
│   ├── ui/screen/LoginScreen.kt
│   └── ui/viewmodel/AuthViewModel.kt
├── child/
│   ├── mvi/action/ChildHomeAction.kt
│   ├── mvi/effect/ChildHomeEffect.kt
│   ├── mvi/state/ChildHomeState.kt
│   ├── ui/screen/ChildHomeScreen.kt
│   └── ui/viewmodel/ChildHomeViewModel.kt
├── core/
│   ├── DateUtil.kt
│   ├── FirebaseResult.kt
│   ├── Money.kt
│   ├── mvi/MviViewModel.kt
│   └── ui/
│       ├── components/
│       │   ├── MoneyField.kt
│       │   └── TransactionRow.kt
│       └── theme/Theme.kt
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
├── navigation/
│   └── AppNavigation.kt
└── parent/
    ├── mvi/action/ParentHomeAction.kt
    ├── mvi/effect/ParentHomeEffect.kt
    ├── mvi/state/ParentHomeState.kt
    ├── ui/screen/ParentHomeScreen.kt
    └── ui/viewmodel/ParentHomeViewModel.kt
```

## UI da Parte 3

- `AppNavigation` observa a sessão, mostra um splash durante a checagem e roteia por `role`.
- `LoginScreen` usa tema escuro neutro, validação e feedback de erro por snackbar.
- `ChildHomeScreen` usa tema neon escuro: saldo, rendimento semanal, meta com progresso e botão "Usar dinheiro" que abre um `ModalBottomSheet` de pedido de saque.
- `ParentHomeScreen` usa tema grafite sóbrio: patrimônio administrado, lista de pendências com aprovar/negar e FAB que abre o sheet de depósito com `FilterChips` (Mesada, Tarefa, Presente, Outros).
- `MoneyField` e `TransactionRow` são componentes compartilhados entre as telas.

## MVI da Parte 2

- `MviViewModel<S, A, E>` concentra `state`, `effect`, `updateState` e `emitEffect`.
- `AuthViewModel` observa a sessão do Firebase, faz login/logout e emite navegação por role.
- `ChildHomeViewModel` combina perfil e transações, calcula rendimento dos últimos 7 dias e envia pedidos de saque.
- `ParentHomeViewModel` observa o filho, lista pendências, deposita, aprova e nega saques.
- A UI não altera estado diretamente: envia `Action` e coleta `StateFlow`/`SharedFlow`.

## Observações

- O `google-services.json` real fica fora do Git; sem ele, o login falha com uma mensagem de Firebase não configurado.
- O `timestamp` retornado por `deposit`/`requestWithdrawal` é aproximado (`Instant.now()`). O valor canônico vem do `serverTimestamp()` pelo Flow.
- A ordenação das transações é feita no cliente para evitar índice composto nesta fase.
