package io.horizontalsystems.walletkit.chain.pirate

import android.content.Context
import android.os.SystemClock
import co.electriccoin.lightwallet.client.LightWalletClient
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.lightwallet.client.model.Response
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.walletkit.core.managers.NodeProfileConnectionTester
import io.horizontalsystems.walletkit.core.managers.NodeProfileTestResult
import io.horizontalsystems.walletkit.entities.NodeAuthType
import io.horizontalsystems.walletkit.entities.NodeCredentials
import io.horizontalsystems.walletkit.entities.NodeProfile
import io.horizontalsystems.walletkit.entities.NodeProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI
import kotlin.time.Duration.Companion.seconds

class PirateNodeProfileTester(context: Context) : NodeProfileConnectionTester {
    private val appContext = context.applicationContext

    override val chainUid: String = BlockchainType.PirateChain.uid
    override val protocol: NodeProtocol = NodeProtocol.LIGHTWALLETD_GRPC
    override val supportedAuthTypes: Set<NodeAuthType> = setOf(NodeAuthType.NONE)

    override suspend fun test(
        profile: NodeProfile,
        credentials: NodeCredentials?,
    ): NodeProfileTestResult {
        val uri = URI(profile.endpoint)
        val endpoint = LightWalletEndpoint(
            host = requireNotNull(uri.host),
            port = uri.port.takeIf { it > 0 } ?: if (uri.scheme == "https") 443 else 80,
            isSecure = profile.tlsMode == io.horizontalsystems.walletkit.entities.NodeTlsMode.REQUIRED,
        )
        val client = LightWalletClient.new(appContext, endpoint, singleRequestTimeout = REQUEST_TIMEOUT)
        val startedAt = SystemClock.elapsedRealtime()

        return try {
            val serverInfo = withTimeoutOrNull(REQUEST_TIMEOUT) {
                (client.getServerInfo() as? Response.Success)?.result
            } ?: return NodeProfileTestResult.Unreachable
            val latestHeight = withTimeoutOrNull(REQUEST_TIMEOUT) {
                (client.getLatestBlockHeight() as? Response.Success)?.result?.value
            } ?: return NodeProfileTestResult.Unreachable

            NodeProfileTestResult.Reachable(
                latencyMs = SystemClock.elapsedRealtime() - startedAt,
                serverInfo = "${serverInfo.lightdInfo.vendor} ${serverInfo.lightdInfo.version} · height $latestHeight".trim(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            NodeProfileTestResult.Unreachable
        } finally {
            withContext(NonCancellable) {
                runCatching { client.dispose() }
            }
        }
    }

    private companion object {
        val REQUEST_TIMEOUT = 5.seconds
    }
}
