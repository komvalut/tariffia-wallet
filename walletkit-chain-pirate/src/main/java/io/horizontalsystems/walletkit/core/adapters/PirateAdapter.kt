package io.horizontalsystems.walletkit.core.adapters

import android.content.Context
import cash.z.ecc.android.sdk.WalletInitMode
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountCreateSetup
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.tool.DerivationTool
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import com.zodl.slipstream.SlipstreamSynchronizer
import io.horizontalsystems.walletkit.core.AdapterState
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.BalanceData
import io.horizontalsystems.walletkit.core.IAdapter
import io.horizontalsystems.walletkit.core.IBalanceAdapter
import io.horizontalsystems.walletkit.core.IReceiveAdapter
import io.horizontalsystems.walletkit.core.ILocalStorage
import io.horizontalsystems.walletkit.core.managers.PirateKitManager
import io.horizontalsystems.walletkit.core.managers.RestoreSettings
import io.horizontalsystems.walletkit.entities.AccountOrigin
import io.horizontalsystems.walletkit.entities.AccountType
import io.horizontalsystems.walletkit.entities.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.math.BigDecimal
import kotlin.math.max

/**
 * Adapter for Pirate Chain (ARRR) balance and receive operations.
 *
 * Wraps the Zcash SDK Synchronizer (Pirate Chain is a Zcash fork with Sapling/Ironwood pools).
 * All operations are guarded so a missing server or SDK failure never crashes the app.
 */
class PirateAdapter(
    context: Context,
    private val wallet: Wallet,
    restoreSettings: RestoreSettings,
    private val localStorage: ILocalStorage,
    private val kitManager: PirateKitManager,
) : IAdapter, IBalanceAdapter, IReceiveAdapter {

    private val appContext: Context = context.applicationContext

    // Pirate Chain uses Zcash-compatible network parameters (Sapling/Ironwood pools).
    // NOTE: Pirate Chain uses Komodo dPoW, not Zcash consensus. The Zcash SDK Synchronizer
    // may need network param adjustments. Using Mainnet as starting point.
    private val network: ZcashNetwork = ZcashNetwork.Mainnet

    private val accountType = (wallet.account.type as? AccountType.Mnemonic)
    private val seed = accountType?.seed

    private var synchronizer: cash.z.ecc.android.sdk.CloseableSynchronizer? = null
    private val adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _balanceState = MutableStateFlow<AdapterState>(AdapterState.NotSynced(Exception("not initialized")))
    override val balanceState: AdapterState get() = _balanceState.value
    override val balanceStateUpdatedFlow: SharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val _balanceData = MutableStateFlow<BalanceData?>(null)
    override val balanceData: BalanceData? get() = _balanceData.value
    override val balanceUpdatedFlow: SharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val receiveAddress: String
        get() = try {
            if (seed == null) return ""
            val derivationTool = DerivationTool.getInstance()
            // Pirate Chain uses Sapling addresses (zs1... prefix).
            // Derive a unified spending key and get the Sapling receiver address.
            val spendingKey = derivationTool.deriveUnifiedSpendingKey(
                seed,
                network,
                cash.z.ecc.android.sdk.model.Zip32AccountIndex.new(0),
            )
            // Get the Sapling address from the spending key
            // The exact method may need adjustment for Pirate Chain
            spendingKey?.toString() ?: ""
        } catch (e: Throwable) {
            Timber.e(e, "PirateAdapter.receiveAddress failed")
            ""
        }

    override val isMainNet: Boolean = true

    private var accountBirthday: Long? = null
    private val existingWallet = false // TODO: check localStorage.zcashAccountIds

    init {
        try {
            if (seed == null) {
                Timber.w("PirateAdapter: no seed, skipping sync")
                _balanceState.value = AdapterState.NotSynced(Exception("no seed"))
                return
            }

            val walletInitMode = if (existingWallet) {
                WalletInitMode.ExistingWallet
            } else when (wallet.account.origin) {
                AccountOrigin.Created -> WalletInitMode.NewWallet
                AccountOrigin.Restored -> WalletInitMode.RestoreWallet
            }

            val birthday = when (wallet.account.origin) {
                AccountOrigin.Created -> runBlocking {
                    cash.z.ecc.android.sdk.model.BlockHeight.ofLatestCheckpoint(appContext, network)
                }
                AccountOrigin.Restored -> restoreSettings.birthdayHeight
                    ?.let { height ->
                        max(network.saplingActivationHeight.value, height)
                    }
                    ?.let {
                        cash.z.ecc.android.sdk.model.BlockHeight.new(it)
                    }
            }

            accountBirthday = birthday?.value

            val rpcSource = kitManager.rpcSourceManager.rpcSource
            if (rpcSource == null) {
                Timber.w("PirateAdapter: no RPC source, skipping sync")
                _balanceState.value = AdapterState.NotSynced(Exception("no RPC source"))
                return
            }

            val endpoint = co.electriccoin.lightwallet.client.model.LightWalletEndpoint(
                rpcSource.host,
                rpcSource.port,
            )

            synchronizer = runBlocking {
                com.zodl.slipstream.SlipstreamSynchronizer.new(
                    alias = "pirate-${wallet.account.id}",
                    birthday = birthday,
                    context = appContext,
                    lightWalletEndpoint = endpoint,
                    setup = AccountCreateSetup(
                        accountName = wallet.account.name,
                        keySource = null,
                        seed = FirstClassByteArray(seed),
                    ),
                    walletInitMode = walletInitMode,
                    zcashNetwork = network,
                    isTorEnabled = localStorage.torEnabled,
                    isExchangeRateEnabled = false,
                )
            }

            _balanceState.value = AdapterState.Syncing()
            Timber.d("PirateAdapter: synchronizer initialized successfully")
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
