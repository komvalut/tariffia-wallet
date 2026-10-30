package io.horizontalsystems.walletkit.chain.pirate

import io.horizontalsystems.walletkit.core.storage.BlockchainSettingsStorage
import io.horizontalsystems.walletkit.core.managers.NodeProfileManager
import io.horizontalsystems.walletkit.entities.NodeAuthType
import io.horizontalsystems.walletkit.entities.NodeProfile
import io.horizontalsystems.walletkit.entities.NodeProtocol
import io.horizontalsystems.walletkit.entities.NodeTlsMode
import io.horizontalsystems.marketkit.models.BlockchainType
import timber.log.Timber
import java.net.URI

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
    private val nodeProfileManager: NodeProfileManager? = null,
) {
    private val blockchainType = BlockchainType.PirateChain

    data class RpcSource(
        val name: String,
        val host: String,
        val port: Int,
        val isSecure: Boolean = true,
        val nodeProfile: NodeProfile? = null,
    )

    // Default public lightwalletd endpoints for Pirate Chain mainnet.
    // Users can override via blockchainSettingsStorage.
    val allRpcSources: List<RpcSource> = try {
        if (endpoint.isBlank()) {
            listOf(
                RpcSource("Pirate lightd1", "lightd1.pirate.black", 443),
                RpcSource("Pirate lightd", "lightd.pirate.black", 443),
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
            nodeProfileManager?.activeProfile(blockchainType.uid, MAINNET)?.let { profile ->
                if (profile.authType != NodeAuthType.NONE || profile.protocol != NodeProtocol.LIGHTWALLETD_GRPC) {
                    return null
                }
                val uri = URI(profile.endpoint)
                return RpcSource(
                    name = profile.name,
                    host = requireNotNull(uri.host),
                    port = uri.port.takeIf { it > 0 } ?: if (profile.tlsMode == NodeTlsMode.REQUIRED) 443 else 80,
                    isSecure = profile.tlsMode == NodeTlsMode.REQUIRED,
                    nodeProfile = profile,
                )
            }
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

    companion object {
        private const val MAINNET = "mainnet"
    }
}
