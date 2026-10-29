package io.horizontalsystems.walletkit.core.adapters

import android.content.Context
import io.horizontalsystems.walletkit.core.AdapterState
import io.horizontalsystems.walletkit.core.BalanceData
import io.horizontalsystems.walletkit.core.IAdapter
import io.horizontalsystems.walletkit.core.IBalanceAdapter
import io.horizontalsystems.walletkit.core.IReceiveAdapter
import io.horizontalsystems.walletkit.core.ILocalStorage
import io.horizontalsystems.walletkit.core.managers.PirateKitManager
import io.horizontalsystems.walletkit.core.managers.RestoreSettings
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
 * Phase 5 skeleton: implements all interfaces with safe defaults.
 * Real sync/balance/address logic is deferred until Zcash SDK network params
 * are verified for Pirate Chain (Komodo dPoW, not Zcash consensus).
 * All operations are guarded so a missing server or SDK failure never crashes.
 */
class PirateAdapter(
    context: Context,
    private val wallet: Wallet,
    restoreSettings: RestoreSettings,
    private val localStorage: ILocalStorage,
    private val kitManager: PirateKitManager,
) : IAdapter, IBalanceAdapter, IReceiveAdapter {

    private val _balanceState = MutableStateFlow<AdapterState>(AdapterState.NotSynced(Exception("not initialized")))
    override val balanceState: AdapterState get() = _balanceState.value
    override val balanceStateUpdatedFlow: SharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val _balanceData = MutableStateFlow<BalanceData?>(null)
    override val balanceData: BalanceData? get() = _balanceData.value
    override val balanceUpdatedFlow: SharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val receiveAddress: String
        get() = try {
            // Phase 5: Pirate Chain uses Sapling (zs1...) and Ironwood (pirate1...) addresses.
            // Address derivation requires Zcash SDK DerivationTool with Pirate network params.
            // TODO: implement when SDK params are verified for Pirate Chain.
            ""
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.receiveAddress failed")
            ""
        }

    override val isMainNet: Boolean = true

    init {
        try {
            Timber.d("PirateAdapter: init (sync deferred - SDK params unverified)")
            _balanceState.value = AdapterState.NotSynced(Exception("SDK params unverified"))
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
