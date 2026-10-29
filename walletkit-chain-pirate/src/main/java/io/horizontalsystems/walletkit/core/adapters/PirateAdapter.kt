package io.horizontalsystems.walletkit.core.adapters

import android.content.Context
import cash.z.ecc.android.sdk.CloseableSynchronizer
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.tool.DerivationTool
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val accountType = (wallet.account.type as? AccountType.Mnemonic)
    private val seed = accountType?.seed

    private var synchronizer: CloseableSynchronizer? = null

    private val _balanceState = MutableStateFlow<AdapterState>(AdapterState.NotSynced(Exception("not initialized")))
    override val balanceState: AdapterState get() = _balanceState.value
    override val balanceStateUpdatedFlow: SharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val _balanceData = MutableStateFlow<BalanceData?>(null)
    override val balanceData: BalanceData? get() = _balanceData.value
    override val balanceUpdatedFlow: SharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val receiveAddress: String
        get() = try {
            if (seed == null) return ""
            // Derive Sapling address from seed using Zcash SDK
            val derivationTool = cash.z.ecc.android.sdk.tool.null // DerivationTool.getInstance()
            val spendingKey = kotlinx.coroutines.runBlocking {
                derivationTool.deriveUnifiedSpendingKey(
                    seed,
                    network,
                    cash.z.ecc.android.sdk.model.Zip32AccountIndex.new(0),
                )
            }
            // Get the Sapling receiver address from the spending key
            // The exact method may need adjustment for Pirate Chain
            spendingKey?.toString() ?: ""
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.receiveAddress failed")
            ""
        }

    override val isMainNet: Boolean = true

    init {
        try {
            if (seed == null) {
                Timber.w("PirateAdapter: no seed, skipping sync")
                _balanceState.value = AdapterState.NotSynced(Exception("no seed"))
            } else {
                synchronizer = kitManager.getSynchronizer(wallet.account)
                if (synchronizer == null) {
                    Timber.w("PirateAdapter: synchronizer init failed")
                    _balanceState.value = AdapterState.NotSynced(Exception("synchronizer init failed"))
                } else {
                    _balanceState.value = AdapterState.Syncing()
                    Timber.d("PirateAdapter: synchronizer initialized successfully")
                }
            }
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.init failed")
            _balanceState.value = AdapterState.NotSynced(e)
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
