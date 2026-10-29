package io.horizontalsystems.walletkit.core.adapters

import io.horizontalsystems.walletkit.core.AdapterState
import io.horizontalsystems.walletkit.core.BalanceData
import io.horizontalsystems.walletkit.core.IAdapter
import io.horizontalsystems.walletkit.core.IBalanceAdapter
import io.horizontalsystems.walletkit.core.IReceiveAdapter
import io.horizontalsystems.walletkit.core.managers.PirateKitManager
import io.horizontalsystems.walletkit.entities.Wallet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.math.BigDecimal

class PirateAdapter(
    private val kitManager: PirateKitManager,
    private val wallet: Wallet,
) : IAdapter, IBalanceAdapter, IReceiveAdapter {

    private val _balanceState = MutableStateFlow<AdapterState>(AdapterState.NotSynced)
    override val balanceState: AdapterState get() = _balanceState.value
    override val balanceStateUpdatedFlow: SharedFlow<Unit> = MutableSharedFlow()

    private val _balanceData = MutableStateFlow<BalanceData?>(null)
    override val balanceData: BalanceData? get() = _balanceData.value
    override val balanceUpdatedFlow: SharedFlow<Unit> = MutableSharedFlow()

    override val receiveAddress: String
        get() = try {
            ""
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.receiveAddress failed")
            ""
        }

    override val isMainNet: Boolean = true

    override fun start() {
        try {
            _balanceState.value = AdapterState.Syncing
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.start failed")
            _balanceState.value = AdapterState.NotSynced
        }
    }

    override fun stop() {
        try {
            _balanceState.value = AdapterState.NotSynced
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.stop failed")
        }
    }

    override fun refresh() {
        try {
            _balanceState.value = AdapterState.Syncing
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.refresh failed")
        }
    }

    override val debugInfo: String
        get() = try {
            "PirateAdapter: ${wallet.token.blockchainType.uid}"
        } catch (e: Throwable) {
            "PirateAdapter: error"
        }
}
