package io.horizontalsystems.walletkit.chain.pirate

import android.content.Context
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.IAdapter
import io.horizontalsystems.walletkit.core.ILocalStorage
import io.horizontalsystems.walletkit.core.chain.ChainPlugin
import io.horizontalsystems.walletkit.core.adapters.PirateAdapter
import io.horizontalsystems.walletkit.core.managers.PirateKitManager
import io.horizontalsystems.walletkit.core.managers.RestoreSettings
import io.horizontalsystems.walletkit.entities.Account
import io.horizontalsystems.walletkit.entities.Wallet
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.marketkit.models.Token
import timber.log.Timber

/**
 * Chain plugin for Pirate Chain (ARRR).
 *
 * Pirate Chain is a Zcash fork (Sapling/Ironwood shielded pools). Uses lightwalletd
 * for sync. All entry points are guarded so a missing server or bad config never
 * crashes App.onCreate.
 */
class PirateChainPlugin(
    private val context: () -> Context,
    private val localStorage: () -> ILocalStorage,
    private val lightwalletdEndpoint: () -> String,
) : ChainPlugin {

    // NOTE: marketkit does not yet define BlockchainType.PirateChain. Using Zcash as a
    // placeholder so the module compiles. The plugin must NOT be registered in MainApp
    // until marketkit adds PirateChain, otherwise it would overwrite the real Zcash plugin.
    override val blockchainType: BlockchainType = BlockchainType.Zcash

    val rpcSourceManager by lazy {
        try {
            PirateRpcSourceManager(App.blockchainSettingsStorage, lightwalletdEndpoint())
        } catch (e: Throwable) {
            Timber.e(e, "PirateChainPlugin: rpcSourceManager init failed")
            null
        }
    }

    val kitManager by lazy {
        try {
            val rpc = rpcSourceManager
            if (rpc == null) {
                Timber.w("PirateChainPlugin: no rpcSourceManager, kitManager unavailable")
                null
            } else {
                PirateKitManager(App.backgroundManager, rpc)
            }
        } catch (e: Throwable) {
            Timber.e(e, "PirateChainPlugin: kitManager init failed")
            null
        }
    }

    override fun createAdapter(wallet: Wallet, restoreSettings: RestoreSettings): IAdapter? {
        return try {
            val km = kitManager ?: return null
            PirateAdapter(
                context(),
                wallet,
                restoreSettings,
                localStorage(),
                km,
            )
        } catch (e: Throwable) {
            Timber.e(e, "PirateChainPlugin.createAdapter failed")
            null
        }
    }

    override fun unlink(account: Account) {
        try {
            kitManager?.unlink(account)
        } catch (e: Throwable) {
            Timber.e(e, "PirateChainPlugin.unlink failed")
        }
    }

    override fun clearAccountData(accountId: String) = Unit

    override fun wcHandlers() = emptyList<io.horizontalsystems.walletkit.modules.walletconnect.handler.IWCHandler>()

    override fun wcWalletRequestHandler() = null

    override suspend fun onAppStart() {
        try {
            // Phase 7: no-op. Sync logic comes in later phases.
        } catch (e: Throwable) {
            Timber.e(e, "PirateChainPlugin.onAppStart failed")
        }
    }

    override suspend fun onEnterForeground() {
        try {
            // Phase 7: no-op.
        } catch (e: Throwable) {
            Timber.e(e, "PirateChainPlugin.onEnterForeground failed")
        }
    }

    override fun backupSyncSourceName(): String? = try {
        rpcSourceManager?.rpcSource?.name
    } catch (e: Throwable) {
        Timber.e(e, "PirateChainPlugin.backupSyncSourceName failed")
        null
    }
}
