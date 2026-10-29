package io.horizontalsystems.walletkit.core.adapters

import android.content.Context
import cash.z.ecc.android.sdk.CloseableSynchronizer
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.ZcashNetwork
import io.horizontalsystems.walletkit.core.AdapterState
import io.horizontalsystems.walletkit.core.BalanceData
import io.horizontalsystems.walletkit.core.IAdapter
import io.horizontalsystems.walletkit.core.IBalanceAdapter
import io.horizontalsystems.walletkit.core.IReceiveAdapter
import io.horizontalsystems.walletkit.core.ILocalStorage
import io.horizontalsystems.walletkit.core.managers.PirateKitManager
import io.horizontalsystems.walletkit.core.managers.RestoreSettings
import io.horizontalsystems.walletkit.entities.AccountType
import io.horizontalsystems.walletkit.entities.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.math.BigDecimal

class PirateAdapter(
    context: Context,
    private val wallet: Wallet,
    restoreSettings: RestoreSettings,
    private val localStorage: ILocalStorage,
    private val kitManager: PirateKitManager,
) : IAdapter, IBalanceAdapter, IReceiveAdapter {

    private val network: ZcashNetwork = ZcashNetwork.Mainnet

    private var synchronizer: CloseableSynchronizer? = null
    private var zcashAccount: Account? = null
    private val adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var currentState: AdapterState = AdapterState.Syncing()

    private val _balanceStateUpdatedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val balanceStateUpdatedFlow: SharedFlow<Unit> = _balanceStateUpdatedFlow
    override val balanceState: AdapterState get() = currentState

    private val _balanceData = MutableStateFlow<BalanceData?>(null)
    override val balanceData: BalanceData? get() = _balanceData.value
    private val _balanceUpdatedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val balanceUpdatedFlow: SharedFlow<Unit> = _balanceUpdatedFlow

    private val _receiveAddress = MutableStateFlow("")
    override val receiveAddress: String get() = _receiveAddress.value
    override val isMainNet: Boolean = true

    init {
        try {
            Timber.i("PirateAdapter: initializing for chain=%s", network)
            val sync = kitManager.getSynchronizer(wallet.account)
            synchronizer = sync
            if (sync == null) {
                setState(AdapterState.NotSynced(Exception("synchronizer init failed")))
            } else {
                setState(AdapterState.Syncing())
                subscribe(sync)
            }
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.init failed")
            setState(AdapterState.NotSynced(e))
        }
    }

    private fun setState(newState: AdapterState) {
        if (currentState != newState) {
            currentState = newState
            _balanceStateUpdatedFlow.tryEmit(Unit)
        }
    }

    private fun subscribe(sync: CloseableSynchronizer) {
        adapterScope.launch {
            try {
                val account = awaitAccount(sync)
                if (account == null) {
                    val error = sync.setupError.value
                        ?: IllegalStateException("Pirate wallet preparation failed or timed out")
                    Timber.e(error, "PirateAdapter: account unavailable")
                    setState(AdapterState.NotSynced(error))
                    return@launch
                }

                zcashAccount = account
                Timber.i("PirateAdapter: account ready, uuid=%s", account.accountUuid)

                deriveReceiveAddress(sync, account)
                launchStatus(sync)
                launchBalances(sync, account)
            } catch (e: Throwable) {
                Timber.e(e, "PirateAdapter.subscribe failed")
                setState(AdapterState.NotSynced(e))
            }
        }
    }

    private suspend fun awaitAccount(sync: CloseableSynchronizer): Account? =
        withTimeoutOrNull(ACCOUNT_WAIT_TIMEOUT_MS) {
            sync.accountsFlow.filterNotNull().firstOrNull { it.isNotEmpty() }?.first()
        }

    private suspend fun deriveReceiveAddress(sync: CloseableSynchronizer, account: Account) {
        try {
            val address = sync.getSaplingAddress(account)
            _receiveAddress.value = address
            Timber.i("PirateAdapter: sapling receive address derived, prefix=%s", address.take(4))
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter: sapling address derivation failed")
        }
    }

    private fun launchStatus(sync: CloseableSynchronizer) {
        adapterScope.launch {
            try {
                sync.status.collect { status ->
                    try {
                        val latest = sync.latestHeight?.value
                        val networkHeight = sync.networkHeight.value?.value
                        Timber.i(
                            "PirateAdapter: status=%s latestHeight=%s networkHeight=%s",
                            status,
                            latest,
                            networkHeight,
                        )
                        when (status) {
                            cash.z.ecc.android.sdk.Synchronizer.Status.SYNCED -> setState(AdapterState.Synced)
                            cash.z.ecc.android.sdk.Synchronizer.Status.SYNCING -> setState(AdapterState.Syncing())
                            else -> setState(AdapterState.Connecting)
                        }
                    } catch (e: Throwable) {
                        Timber.e(e, "PirateAdapter: status handling failed")
                        setState(AdapterState.NotSynced(e))
                    }
                }
            } catch (e: Throwable) {
                Timber.e(e, "PirateAdapter: status flow failed")
                setState(AdapterState.NotSynced(e))
            }
        }
    }

    private fun launchBalances(sync: CloseableSynchronizer, account: Account) {
        adapterScope.launch {
            try {
                sync.walletBalances.collect { balances ->
                    try {
                        val balance = balances?.get(account.accountUuid)
                        if (balance != null) {
                            val availableZatoshi = balance.sapling.available +
                                balance.orchard.available +
                                balance.ironwood.available
                            val pendingZatoshi = balance.sapling.pending +
                                balance.orchard.pending +
                                balance.ironwood.pending
                            val available = toZec(availableZatoshi)
                            val pending = toZec(pendingZatoshi)
                            val unshielded = toZec(balance.unshielded)

                            _balanceData.value = BalanceData(
                                available = available,
                                pending = pending,
                                unshielded = unshielded,
                            )
                            _balanceUpdatedFlow.tryEmit(Unit)
                            Timber.i(
                                "PirateAdapter: balance available=%s pending=%s unshielded=%s",
                                available,
                                pending,
                                unshielded,
                            )
                        }
                    } catch (e: Throwable) {
                        Timber.e(e, "PirateAdapter: balance handling failed")
                    }
                }
            } catch (e: Throwable) {
                Timber.e(e, "PirateAdapter: walletBalances flow failed")
            }
        }
    }

    private fun toZec(zatoshi: cash.z.ecc.android.sdk.model.Zatoshi): BigDecimal =
        try {
            zatoshi.convertZatoshiToZec(DECIMAL_COUNT)
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter: zatoshi conversion failed")
            BigDecimal.ZERO
        }

    suspend fun send(amount: BigDecimal, address: String, memo: String): List<String> {
        return try {
            val sync = synchronizer ?: throw IllegalStateException("Synchronizer not initialized")
            val account = zcashAccount ?: awaitAccount(sync)
                ?: throw IllegalStateException("No account available")
            zcashAccount = account

            val proposal = sync.proposeTransfer(
                account = account,
                recipient = address,
                amount = amount.convertZecToZatoshi(),
                memo = memo,
            )

            val spendingKey = cash.z.ecc.android.sdk.tool.DerivationTool.getInstance().deriveUnifiedSpendingKey(
                (wallet.account.type as AccountType.Mnemonic).seed,
                network,
                cash.z.ecc.android.sdk.model.Zip32AccountIndex.new(0),
            )

            val results = sync.createProposedTransactions(proposal, spendingKey).toList()
            val txHashes = mutableListOf<String>()
            results.forEach { result ->
                when (result) {
                    is cash.z.ecc.android.sdk.model.TransactionSubmitResult.Success -> {
                        txHashes.add(result.txIdString())
                        Timber.i("PirateAdapter: transaction submitted: %s", result.txIdString())
                    }
                    is cash.z.ecc.android.sdk.model.TransactionSubmitResult.Failure -> {
                        val errorMsg = "Transaction submission failed. TxId: ${result.txIdString()}, gRPC error: ${result.grpcError}, Code: ${result.code}, Description: ${result.description ?: "None"}"
                        Timber.e("PirateAdapter: %s", errorMsg)
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
            setState(AdapterState.Syncing())
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.start failed")
            setState(AdapterState.NotSynced(e))
        }
    }

    override fun stop() {
        try {
            synchronizer?.close()
            synchronizer = null
            adapterScope.cancel()
            setState(AdapterState.NotSynced(Exception("stopped")))
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.stop failed")
        }
    }

    override fun refresh() {
        try {
            setState(AdapterState.Syncing())
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.refresh failed")
        }
    }

    override val debugInfo: String
        get() = try {
            "PirateAdapter: ${wallet.token.blockchainType.uid}, sync=$currentState, latest=${synchronizer?.latestHeight?.value}"
        } catch (e: Throwable) {
            "PirateAdapter: error"
        }

    companion object {
        private const val ACCOUNT_WAIT_TIMEOUT_MS = 90_000L
        private const val DECIMAL_COUNT = 8
    }
}
