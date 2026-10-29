package io.horizontalsystems.walletkit.chain.pirate

import io.horizontalsystems.walletkit.core.storage.BlockchainSettingsStorage
import io.horizontalsystems.marketkit.models.BlockchainType
import timber.log.Timber

/**
 * Manages the lightwalletd endpoint for Pirate Chain (ARRR).
 * Pirate Chain is a Zcash fork (Sapling/Ironwood shielded pools), so it uses
 * the same lightwalletd gRPC protocol. The endpoint is configurable.
 *
 * All access is guarded: if the endpoint is blank or invalid, rpcSource returns null
 * instead of throwing. This prevents startup crashes when config is missing.
 */
class PirateRpcSourceManager(
    private val blockchainSettingsStorage: BlockchainSettingsStorage,
    private val endpoint: String,
) {
    private val blockchainType = BlockchainType.PirateChain

    data class RpcSource(val name: String, val host: String, val port: Int)

    // Default public lightwalletd endpoints for Pirate Chain mainnet.
    // Users can override via blockchainSettingsStorage.
    val allRpcSources: List<RpcSource> = try {
        if (endpoint.isBlank()) {
            listOf(
                RpcSource("Pirate lightd1", "lightd1.pirate.black", 443),
                RpcSource("Pirate lightd2", "lightd2.pirate.black", 443),
            )
        } else {
            val host = endpoint.substringBefore(":")
            val port = endpoint.substringAfter(":", "443").toIntOrNull() ?: 443
            listOf(RpcSource("Custom", host, port))
        }
    } catch (e: Throwable) {
        Timber.e(e, "PirateRpcSourceManager: failed to parse endpoint")
        emptyList()
    }

    val rpcSource: RpcSource?
        get() = try {
            if (allRpcSources.isEmpty()) null
            else {
                val name = try { blockchainSettingsStorage.evmSyncSourceUrl(blockchainType) } catch (_: Throwable) { null }
                allRpcSources.firstOrNull { it.name == name } ?: allRpcSources.firstOrNull()
            }
        } catch (e: Throwable) {
            Timber.e(e, "PirateRpcSourceManager.rpcSource failed")
            null
        }

    val birthdayHeight: Long
        get() = try {
            // Default birthday height. Should be persisted in storage in production.
            0L
        } catch (e: Throwable) {
            Timber.e(e, "PirateRpcSourceManager.birthdayHeight failed")
            0L
        }
}
