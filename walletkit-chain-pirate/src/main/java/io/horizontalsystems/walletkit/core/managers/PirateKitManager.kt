package io.horizontalsystems.walletkit.core.managers

import android.content.Context
import cash.z.ecc.android.sdk.CloseableSynchronizer
import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.android.sdk.WalletInitMode
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountCreateSetup
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import com.zodl.slipstream.SlipstreamSynchronizer
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.BackgroundManager
import io.horizontalsystems.walletkit.entities.Account as WalletAccount
import io.horizontalsystems.walletkit.entities.AccountOrigin
import io.horizontalsystems.walletkit.entities.AccountType
import io.horizontalsystems.walletkit.chain.pirate.PirateRpcSourceManager
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Manages Pirate Chain (ARRR) kit instances.
 *
 * Pirate Chain is a Zcash fork (Sapling/Ironwood shielded pools). Uses the Zcash SDK
 * Synchronizer with Pirate Chain lightwalletd endpoint. All operations are guarded.
 */
class PirateKitManager(
    private val backgroundManager: BackgroundManager,
    val rpcSourceManager: PirateRpcSourceManager,
) {
    var currentAccount: WalletAccount? = null
        private set

    var synchronizer: CloseableSynchronizer? = null
        private set

    /**
     * Creates or returns a synchronizer for the given account.
     * Returns null if initialization fails. Never throws.
     */
    fun getSynchronizer(account: WalletAccount): CloseableSynchronizer? {
        return try {
            if (synchronizer != null && currentAccount == account) {
                return synchronizer
            }

            // Stop existing synchronizer if account changed
            synchronizer?.close()
            synchronizer = null
            currentAccount = null

            val accountType = (account.type as? AccountType.Mnemonic) ?: run {
                Timber.w("PirateKitManager: unsupported account type")
                return null
            }

            val rpcSource = rpcSourceManager.rpcSource ?: run {
                Timber.w("PirateKitManager: no RPC source available")
                return null
            }

            val endpoint = LightWalletEndpoint(rpcSource.host, rpcSource.port, true)

            // Pirate Chain uses Zcash-compatible network parameters.
            // Using ZcashNetwork.Mainnet as base (Pirate is a Zcash fork).
            // NOTE: Pirate Chain uses Komodo dPoW, not Zcash consensus.
            // Network params may need adjustment for full compatibility.
            val network = ZcashNetwork.Mainnet

            val existingWallet = false // TODO: check localStorage
            val walletInitMode = if (existingWallet) {
                WalletInitMode.ExistingWallet
            } else when (account.origin) {
                AccountOrigin.Created -> WalletInitMode.NewWallet
                AccountOrigin.Restored -> WalletInitMode.RestoreWallet
            }

            val birthday = if (account.origin == AccountOrigin.Created) {
                null // SDK will use latest checkpoint
            } else {
                rpcSourceManager.birthdayHeight.takeIf { it > 0 }?.let {
                    cash.z.ecc.android.sdk.model.BlockHeight.new(it.toLong())
                }
            }

            val seed = accountType.seed
            val synchronizerInstance = runBlocking {
                SlipstreamSynchronizer.new(
                    alias = "pirate-${account.id}",
                    birthday = birthday,
                    context = App.instance,
                    lightWalletEndpoint = endpoint,
                    setup = AccountCreateSetup(
                        accountName = account.name,
                        keySource = null,
                        seed = FirstClassByteArray(seed),
                    ),
                    walletInitMode = walletInitMode,
                    zcashNetwork = network,
                    isTorEnabled = false,
                    isExchangeRateEnabled = false,
                )
            }

            synchronizer = synchronizerInstance
            currentAccount = account
            synchronizerInstance
        } catch (e: Throwable) {
            Timber.e(e, "PirateKitManager.getSynchronizer failed")
            synchronizer?.close()
            synchronizer = null
            currentAccount = null
            null
        }
    }

    fun unlink(account: WalletAccount) {
        try {
            if (account == currentAccount) {
                synchronizer?.close()
                synchronizer = null
                currentAccount = null
            }
        } catch (e: Throwable) {
            Timber.e(e, "PirateKitManager.unlink failed")
        }
    }
}
