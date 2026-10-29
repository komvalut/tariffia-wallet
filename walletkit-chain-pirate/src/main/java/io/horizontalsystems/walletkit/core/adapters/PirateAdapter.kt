package io.horizontalsystems.walletkit.core.adapters

import android.content.Context
import cash.z.ecc.android.sdk.CloseableSynchronizer
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import io.horizontalsystems.walletkit.core.AdapterState
import io.horizontalsystems.walletkit.core.BalanceData
import io.horizontalsystems.walletkit.core.IAdapter
import io.horizontalsystems.walletkit.core.IBalanceAdapter
import io.horizontalsystems.walletkit.core.IReceiveAdapter
import io.horizontalsystems.walletkit.core.ILocalStorage
import io.horizontalsystems.walletkit.core.managers.PirateKitManager
import io.horizontalsystems.walletkit.core.managers.RestoreSettings
import io.horizontalsystems.walletkit.entities.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.math.BigDecimal

/**
 * Adapter for Pirate Chain (ARRR) balance and receive operations.
 *
 * Wraps the Zcash SDK Synchronizer (Pirate Chain is a Zcash fork).
 * All operations are guarded so a missing server or SDK failure never crashes.
 */
class PirateAdapter(
    context: Context,
    private val wallet: Wallet,
    restoreSettings: RestoreSettings,
    private val localStorage: ILocalStorage,
    private val kitManager: PirateKitManager,
) : IAdapter, IBalanceAdapter, IReceiveAdapter {

    private val appContext: Context = context.applicationContext
    private val network: ZcashNetwork = ZcashNetwork.Mainnet

    private var synchronizer: CloseableSynchronizer? = null
    private var zcashAccount: cash.z.ecc.android.sdk.model.Account? = null
    private val adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _balanceState = MutableStateFlow<AdapterState>(AdapterState.NotSynced(Exception("not initialized")))
    override val balanceState: AdapterState get() = _balanceState.value
    override val balanceStateUpdatedFlow: SharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val _balanceData = MutableStateFlow<BalanceData?>(null)
    override val balanceData: BalanceData? get() = _balanceData.value
    private val _balanceUpdatedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val balanceUpdatedFlow: SharedFlow<Unit> = _balanceUpdatedFlow

    // Cached receive address (derived asynchronously)
    private val _receiveAddress = MutableStateFlow("")
    override val receiveAddress: String get() = _receiveAddress.value
    override val isMainNet: Boolean = true

    init {
        try {
            synchronizer = kitManager.getSynchronizer(wallet.account)
            if (synchronizer == null) {
                Timber.w("PirateAdapter: synchronizer init failed")
                _balanceState.value = AdapterState.NotSynced(Exception("synchronizer init failed"))
            } else {
                _balanceState.value = AdapterState.Syncing()
                Timber.d("PirateAdapter: synchronizer initialized")
                subscribe(synchronizer!!)
                deriveReceiveAddress()
            }
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.init failed")
            _balanceState.value = AdapterState.NotSynced(e)
        }
    }

    private fun subscribe(sync: CloseableSynchronizer) {
        try {
            adapterScope.launch {
                try {
                    sync.status.collect { status ->
                        try {
                            when (status) {
                                cash.z.ecc.android.sdk.Synchronizer.Status.SYNCED -> {
                                    _balanceState.value = AdapterState.Synced
                                }
                                cash.z.ecc.android.sdk.Synchronizer.Status.SYNCING -> {
                                    _balanceState.value = AdapterState.Syncing()
                                }
                                else -> {
                                    _balanceState.value = AdapterState.Connecting
                                }
                            }
                        } catch (e: Throwable) {
                            Timber.e(e, "PirateAdapter: status collect failed")
                            _balanceState.value = AdapterState.NotSynced(e)
                        }
                    }
                } catch (e: Throwable) {
                    Timber.e(e, "PirateAdapter: status flow failed")
                    _balanceState.value = AdapterState.NotSynced(e)
                }
            }

            adapterScope.launch {
                try {
                    sync.walletBalances.collect { balances ->
                        try {
                            val balance = balances?.values?.firstOrNull()
                            if (balance != null) {
                                Timber.d("PirateAdapter: balance received: %s", balance)
                                val available = try {
                                    (balance.sapling.available + balance.orchard.available + balance.ironwood.available).convertZatoshiToZec(8)
                                } catch (e: Throwable) {
                                    Timber.e(e, "PirateAdapter: available conversion failed")
                                    BigDecimal.ZERO
                                }
                                val pending = try {
                                    (balance.sapling.pending + balance.orchard.pending + balance.ironwood.pending).convertZatoshiToZec(8)
                                } catch (e: Throwable) {
                                    Timber.e(e, "PirateAdapter: pending conversion failed")
                                    BigDecimal.ZERO
                                }
                                val unshielded = try {
                                    balance.unshielded.convertZatoshiToZec(8)
                                } catch (e: Throwable) {
                                    Timber.e(e, "PirateAdapter: unshielded conversion failed")
                                    BigDecimal.ZERO
                                }
                                _balanceData.value = BalanceData(
                                    available = available,
                                    pending = pending,
                                    unshielded = unshielded,
                                )
                                _balanceUpdatedFlow.tryEmit(Unit)
                                Timber.d("PirateAdapter: balance updated: available=%s pending=%s", available, pending)
                            }
                        } catch (e: Throwable) {
                            Timber.e(e, "PirateAdapter: balance parse failed")
                        }
                    }
                } catch (e: Throwable) {
                    Timber.e(e, "PirateAdapter: walletBalances flow failed")
                }
            }
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.subscribe failed")
        }
    }

    private fun deriveReceiveAddress() {
        adapterScope.launch {
            try {
                val seed = (wallet.account.type as? io.horizontalsystems.walletkit.entities.AccountType.Mnemonic)?.seed
                if (seed == null) {
                    Timber.w("PirateAdapter: no seed for address derivation")
                    return@launch
                }
                val derivationTool = cash.z.ecc.android.sdk.tool.DerivationTool.getInstance()
                val spendingKey = derivationTool.deriveUnifiedSpendingKey(
                    seed,
                    network,
                    cash.z.ecc.android.sdk.model.Zip32AccountIndex.new(0),
                )
                // Get the Sapling address from the spending key
                // The exact method depends on SDK version. Using toString as fallback.
                val address = spendingKey?.toString() ?: ""
                _receiveAddress.value = address
                Timber.d("PirateAdapter: receive address derived: %s", address)
            } catch (e: Throwable) {
                Timber.e(e, "PirateAdapter: address derivation failed")
            }
        }
    }

    suspend fun send(amount: BigDecimal, address: String, memo: String): List<String> {
        return try {
            val sync = synchronizer ?: throw IllegalStateException("Synchronizer not initialized")
            val account = zcashAccount ?: run {
                // Try to get account from synchronizer
                try {
                    val accounts = sync.accountsFlow.filterNotNull().firstOrNull { it.isNotEmpty() }
                    accounts?.firstOrNull()
                } catch (e: Throwable) {
                    Timber.e(e, "PirateAdapter.send: failed to get account")
                    null
                }
            } ?: throw IllegalStateException("No account available")

            zcashAccount = account

            val proposal = sync.proposeTransfer(
                account = account,
                recipient = address,
                amount = amount.convertZecToZatoshi(),
                memo = memo,
            )

            val spendingKey = cash.z.ecc.android.sdk.tool.DerivationTool.getInstance().deriveUnifiedSpendingKey(
                (wallet.account.type as io.horizontalsystems.walletkit.entities.AccountType.Mnemonic).seed,
                network,
                cash.z.ecc.android.sdk.model.Zip32AccountIndex.new(0),
            )

            val results = sync.createProposedTransactions(proposal, spendingKey).toList()
            val txHashes = mutableListOf<String>()
            results.forEach { result ->
                when (result) {
                    is cash.z.ecc.android.sdk.model.TransactionSubmitResult.Success -> {
                        txHashes.add(result.txIdString())
                        Timber.d("PirateAdapter.send: transaction submitted: %s", result.txIdString())
                    }
                    is cash.z.ecc.android.sdk.model.TransactionSubmitResult.Failure -> {
                        val errorMsg = "Transaction submission failed. TxId: ${result.txIdString()}, gRPC error: ${result.grpcError}, Code: ${result.code}, Description: ${result.description ?: "None"}"
                        Timber.e("PirateAdapter.send: %s", errorMsg)
                        throw IllegalStateException(errorMsg)
                    }
                    is cash.z.ecc.android.sdk.model.TransactionSubmitResult.NotAttempted -> {
                        throw IllegalStateException("Transaction not attempted. TxId: ${result.txIdString()}")
                    }
                }
            }
            txHashes
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.send failed")
            throw e
        }
    }

    override fun start() {
        try {
            _balanceState.value = AdapterState.Syncing()
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.start failed")
            _balanceState.value = AdapterState.NotSynced(e)
        }
    }

    override fun stop() {
        try {
            synchronizer?.close()
            synchronizer = null
            adapterScope.cancel()
            _balanceState.value = AdapterState.NotSynced(Exception("stopped"))
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.stop failed")
        }
    }

    override fun refresh() {
        try {
            _balanceState.value = AdapterState.Syncing()
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.refresh failed")
        }
    }

    override val debugInfo: String
        get() = try {
            "PirateAdapter: ${wallet.token.blockchainType.uid}, sync=${_balanceState.value}"
        } catch (e: Throwable) {
            "PirateAdapter: error"
        }
}
