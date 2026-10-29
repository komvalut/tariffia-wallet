package io.horizontalsystems.walletkit.core.managers

import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.BackgroundManager
import io.horizontalsystems.walletkit.entities.Account as WalletAccount
import io.horizontalsystems.walletkit.entities.AccountType
import io.horizontalsystems.walletkit.chain.pirate.PirateRpcSourceManager
import timber.log.Timber

/**
 * Manages Pirate Chain (ARRR) kit instances.
 *
 * Phase 3: skeleton that wraps the Zcash SDK Synchronizer.
 * Real SDK calls are guarded so a missing server or bad config never crashes.
 */
class PirateKitManager(
    private val backgroundManager: BackgroundManager,
    private val rpcSourceManager: PirateRpcSourceManager,
) {
    var currentAccount: WalletAccount? = null
        private set

    /**
     * Returns a kit wrapper for the given account. Returns null if the account type
     * is unsupported or if initialization fails. Never throws.
     */
    fun getPirateKitWrapper(account: WalletAccount): PirateKitWrapper? {
        return try {
            val accountType = (account.type as? AccountType.Mnemonic) ?: run {
                Timber.w("PirateKitManager: unsupported account type")
                return null
            }

            val rpcSource = rpcSourceManager.rpcSource ?: run {
                Timber.w("PirateKitManager: no RPC source available")
                return null
            }

            // Phase 3: create synchronizer with Zcash SDK.
            // NOTE: Pirate Chain uses Komodo dPoW, not Zcash consensus.
            // The Zcash SDK may need network param adjustments.
            // For now, return a stub wrapper. Real sync comes when SDK params are verified.
            currentAccount = account
            PirateKitWrapper(account, rpcSource)
        } catch (e: Throwable) {
            Timber.e(e, "PirateKitManager.getPirateKitWrapper failed")
            null
        }
    }

    fun unlink(account: WalletAccount) {
        try {
            if (account == currentAccount) {
                currentAccount = null
            }
        } catch (e: Throwable) {
            Timber.e(e, "PirateKitManager.unlink failed")
        }
    }
}

class PirateKitWrapper(
    val account: WalletAccount,
    val rpcSource: PirateRpcSourceManager.RpcSource,
)
