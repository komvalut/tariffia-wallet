package io.horizontalsystems.walletkit.chain.pirate

import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.IAdapter
import io.horizontalsystems.walletkit.core.chain.ChainPlugin
import io.horizontalsystems.walletkit.core.managers.RestoreSettings
import io.horizontalsystems.walletkit.core.managers.PirateKitManager
import io.horizontalsystems.walletkit.entities.Account
import io.horizontalsystems.walletkit.core.adapters.PirateAdapter
import io.horizontalsystems.walletkit.entities.Wallet
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.walletkit.modules.walletconnect.handler.IWCHandler
import timber.log.Timber

/**
 * Chain plugin for Pirate Chain (ARRR).
 *
 * Pirate Chain is a Zcash fork (Sapling/Ironwood shielded pools). Uses lightwalletd
 * for sync. All entry points are guarded so a missing server or bad config never
 * crashes App.onCreate.
 *
 * Phase 1: registration + safe skeleton. Sync/balance/send come in later phases.
 */
class PirateChainPlugin(
    private val lightwalletdEndpoint: () -> String,
) : ChainPlugin {

    override val blockchainType: BlockchainType = BlockchainType.Zcash // TODO: replace with BlockchainType.PirateChain once marketkit adds it

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
            val wrapper = km.getPirateKitWrapper(wallet.account) ?: return null
            PirateAdapter(km, wallet)
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

    override fun wcHandlers() = emptyList<IWCHandler>()

    override fun wcWalletRequestHandler() = null

    override suspend fun onAppStart() {
        try {
            // Phase 1: no-op. Sync logic comes in phase 2.
        } catch (e: Throwable) {
            Timber.e(e, "PirateChainPlugin.onAppStart failed")
        }
    }

    override suspend fun onEnterForeground() {
        try {
            // Phase 1: no-op.
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
