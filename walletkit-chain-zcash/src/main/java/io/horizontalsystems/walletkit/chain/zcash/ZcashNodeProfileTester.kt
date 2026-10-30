package io.horizontalsystems.walletkit.chain.zcash

import android.content.Context
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.walletkit.core.managers.NodeProfileConnectionTester
import io.horizontalsystems.walletkit.core.managers.NodeProfileTestResult
import io.horizontalsystems.walletkit.entities.NodeAuthType
import io.horizontalsystems.walletkit.entities.NodeCredentials
import io.horizontalsystems.walletkit.entities.NodeProfile
import io.horizontalsystems.walletkit.entities.NodeProtocol
import io.horizontalsystems.walletkit.entities.NodeTlsMode
import kotlinx.coroutines.CancellationException
import java.net.URI

class ZcashNodeProfileTester(private val context: Context) : NodeProfileConnectionTester {
    override val chainUid: String = BlockchainType.Zcash.uid
    override val protocol: NodeProtocol = NodeProtocol.LIGHTWALLETD_GRPC
    override val supportedAuthTypes: Set<NodeAuthType> = setOf(NodeAuthType.NONE)

    override suspend fun test(
        profile: NodeProfile,
        credentials: NodeCredentials?,
    ): NodeProfileTestResult {
        return try {
            val result = ZcashEndpointPinger.ping(context, listOf(profile.endpoint)) { endpoint ->
                val uri = URI(endpoint)
                LightWalletEndpoint(
                    host = requireNotNull(uri.host),
                    port = uri.port.takeIf { it > 0 } ?: if (profile.tlsMode == NodeTlsMode.REQUIRED) 443 else 80,
                    isSecure = profile.tlsMode == NodeTlsMode.REQUIRED,
                )
            }.singleOrNull()

            if (result?.isValid == true) {
                NodeProfileTestResult.Reachable(result.responseTime.toLong())
            } else {
                NodeProfileTestResult.Unreachable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            NodeProfileTestResult.Unreachable
        }
    }
}
