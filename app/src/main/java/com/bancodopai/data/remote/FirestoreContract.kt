package com.bancodopai.data.remote

internal object FirestoreContract {

    const val USERS = "users"
    const val TRANSACTIONS = "transactions"
    const val INVITES = "invites"

    // users
    const val NAME = "name"
    const val EMAIL = "email"
    const val ROLE = "role"
    const val BALANCE_CENTS = "balanceCents"
    const val GOAL_NAME = "goalName"
    const val GOAL_TARGET_CENTS = "goalTargetCents"
    const val PARENT_ID = "parentId"
    const val PARENT_IDS = "parentIds"
    const val WEEKLY_INTEREST_RATE = "weeklyInterestRatePercent"
    const val LAST_INTEREST_APPLIED_AT = "lastInterestAppliedAt"
    const val INVITE_CODE = "inviteCode"

    // invites
    const val PARENT_NAME = "parentName"

    // transactions
    const val CHILD_ID = "childId"
    const val TYPE = "type"
    const val AMOUNT_CENTS = "amountCents"
    const val DESCRIPTION = "description"
    const val STATUS = "status"
    const val TIMESTAMP = "timestamp"
}
