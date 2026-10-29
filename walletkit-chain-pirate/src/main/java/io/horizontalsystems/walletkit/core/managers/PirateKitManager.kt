package io.horizontalsystems.walletkit.core.managers

import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.BackgroundManager
import io.horizontalsystems.walletkit.core.BackgroundManagerState
import io.horizontalsystems.walletkit.entities.Account
import io.horizontalsystems.walletkit.entities.AccountType
import io.horizontalsystems.walletkit.chain.pirate.PirateRpcSourceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages Pirate Chain (ARRR) kit instances.
 *
 * Pirate Chain is a Zcash fork (Sapling/Ironwood shielded pools). This manager
 * wraps the Zcash SDK Synchronizer. All operations are guarded so that a missing
 * lightwalletd server, missing keys, or SDK failure never crashes the app.
 *
 * Phase 1: skeleton only. Sync/balance/send are not yet implemented.
 */
class PirateKitManager(
    private val backgroundManager: BackgroundManager,
    private val rpcSourceManager: PirateRpcSourceManager,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    var currentAccount: Account? = null
        private set

    /**
     * Returns a kit wrapper for the given account. Returns null if the account type
     * is unsupported or if initialization fails. Never throws.
     */
    fun getPirateKitWrapper(account: Account): PirateKitWrapper? {
        return try {
            val rpcSource = rpcSourceManager.rpcSource
            if (rpcSource == null) {
                Timber.w("PirateKitManager: no RPC source available, skipping")
                return null
            }
            // Phase 1: return a stub wrapper. Real SDK integration comes in phase 2.
            PirateKitWrapper(account, rpcSource)
        } catch (e: Throwable) {
            Timber.e(e, "PirateKitManager.getPirateKitWrapper failed")
            null
        }
    }

    fun unlink(account: Account) {
        try {
            if (account == currentAccount) {
                currentAccount = null
            }
        } catch (e: Throwable) {
            Timber.e(e, "PirateKitManager.unlink failed")
        }
    }
}

/**
 * Phase 1 stub. Will hold the actual Synchronizer instance in phase 2.
 */
class PirateKitWrapper(
    val account: Account,
    val rpcSource: PirateRpcSourceManager.RpcSource,
)
