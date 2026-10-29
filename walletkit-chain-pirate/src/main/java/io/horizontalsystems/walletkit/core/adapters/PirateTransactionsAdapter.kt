package io.horizontalsystems.walletkit.core.adapters

import io.horizontalsystems.walletkit.core.AdapterState
import io.horizontalsystems.walletkit.core.ITransactionsAdapter
import io.horizontalsystems.walletkit.entities.LastBlockInfo
import io.horizontalsystems.walletkit.entities.transactionrecords.TransactionRecord
import io.horizontalsystems.walletkit.modules.transactions.FilterTransactionType
import io.horizontalsystems.marketkit.models.Token
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import timber.log.Timber

/**
 * Adapter for Pirate Chain (ARRR) transaction history.
 *
 * Phase 2 skeleton: implements ITransactionsAdapter with safe defaults.
 * Real transaction reading from lightwalletd-synced local DB comes in phase 3.
 */
class PirateTransactionsAdapter : ITransactionsAdapter {

    override val explorerTitle: String = "Pirate Chain Explorer"

    private val _transactionsState = AdapterState.NotSynced(Exception("not initialized"))
    override val transactionsState: AdapterState get() = _transactionsState
    override val transactionsStateUpdatedFlow: Flow<Unit> = emptyFlow()

    override val lastBlockInfo: LastBlockInfo? = null
    override val lastBlockUpdatedFlow: Flow<Unit> = emptyFlow()

    override suspend fun getTransactions(
        from: TransactionRecord?,
        token: Token?,
        limit: Int,
        transactionType: FilterTransactionType,
        address: String?,
    ): List<TransactionRecord> {
        return try {
            emptyList()
        } catch (e: Throwable) {
            Timber.e(e, "PirateTransactionsAdapter.getTransactions failed")
            emptyList()
        }
    }

    override fun getTransactionRecordsFlow(
        token: Token?,
        transactionType: FilterTransactionType,
        address: String?,
    ): Flow<List<TransactionRecord>> = emptyFlow()

    override fun getTransactionUrl(transactionHash: String): String =
        "https://explorer.piratechain.com/tx/$transactionHash"
}
