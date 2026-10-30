package io.horizontalsystems.walletkit.core.managers

import io.horizontalsystems.walletkit.core.storage.NodeProfileStorage
import io.horizontalsystems.walletkit.entities.NodeAuthType
import io.horizontalsystems.walletkit.entities.NodeCredentials
import io.horizontalsystems.walletkit.entities.NodeProfile
import io.horizontalsystems.walletkit.entities.NodeProtocol
import io.horizontalsystems.walletkit.entities.NodeTlsMode
import io.horizontalsystems.walletkit.security.NodeCredentialCipher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class NodeProfileManager(
    private val storage: NodeProfileStorage,
    private val credentialCipher: NodeCredentialCipher,
) {
    private val testers = ConcurrentHashMap<TesterKey, NodeProfileConnectionTester>()
    private val _profileUpdatedFlow = MutableSharedFlow<NodeProfileChange>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val profileUpdatedFlow: SharedFlow<NodeProfileChange> = _profileUpdatedFlow.asSharedFlow()
    val profilesFlow = storage.observeAll()
    val chainUidsFlow = profilesFlow.map { profiles -> profiles.map { it.chainUid }.distinct() }

    fun profiles(chainUid: String, network: String): List<NodeProfile> =
        storage.getProfiles(chainUid, network)

    fun allProfiles(): List<NodeProfile> = storage.getAll()

    fun activeProfile(chainUid: String, network: String): NodeProfile? =
        storage.getSelected(chainUid, network)

    fun save(profile: NodeProfile, credentials: NodeCredentials? = null, notify: Boolean = true): NodeProfile {
        validate(profile)
        require(!profile.isBuiltIn) { "Built-in node profiles cannot be edited" }

        val profileId = profile.profileId.ifBlank { UUID.randomUUID().toString() }
        val previousProfile = storage.getProfile(profile.chainUid, profile.network, profileId)
        require(!profile.isSelected || isProfileSupported(profile)) {
            "This node profile is not supported by the current transport"
        }
        val credentialRef = when (profile.authType) {
            NodeAuthType.NONE -> null
            NodeAuthType.BASIC,
            NodeAuthType.BEARER -> UUID.randomUUID().toString()
        }
        val savedProfile = profile.copy(
            profileId = profileId,
            credentialRef = credentialRef,
            isSelected = profile.isSelected,
            isBuiltIn = false,
        )
        val ciphertext = credentialRef?.let {
            validateCredentials(profile.authType, credentials)
            credentialCipher.encrypt(savedProfile, requireNotNull(credentials))
        }

        storage.save(savedProfile, ciphertext, previousProfile?.credentialRef)
        if (savedProfile.isSelected && notify) emitChange(savedProfile)
        return savedProfile
    }

    fun setActive(chainUid: String, network: String, profileId: String, notify: Boolean = true) {
        val profile = requireNotNull(storage.getProfile(chainUid, network, profileId)) {
            "Node profile not found"
        }
        require(!profile.isBuiltIn) { "Built-in node profiles are not managed by NodeProfileManager" }
        require(isProfileSupported(profile)) {
            "This node profile is not supported by the current transport"
        }
        storage.select(chainUid, network, profileId)
        if (notify) emitChange(profile.copy(isSelected = true))
    }

    fun clearActive(chainUid: String, network: String, notify: Boolean = true) {
        storage.clearSelection(chainUid, network)
        if (notify) _profileUpdatedFlow.tryEmit(NodeProfileChange(chainUid, network, null))
    }

    fun delete(chainUid: String, network: String, profileId: String, notify: Boolean = true) {
        val profile = storage.getProfile(chainUid, network, profileId) ?: return
        require(!profile.isBuiltIn) { "Built-in node profiles cannot be deleted" }
        storage.delete(chainUid, network, profileId)
        if (profile.isSelected && notify) {
            _profileUpdatedFlow.tryEmit(NodeProfileChange(chainUid, network, null))
        }
    }

    fun deleteByEndpoint(chainUid: String, network: String, endpoint: String, notify: Boolean = true) {
        storage.getProfiles(chainUid, network)
            .firstOrNull { it.endpoint == endpoint }
            ?.let { delete(chainUid, network, it.profileId, notify) }
    }

    fun credentials(profile: NodeProfile): NodeCredentials? {
        val credentialRef = profile.credentialRef ?: return null
        val record = requireNotNull(storage.getCredential(credentialRef)) { "Node credentials are missing" }
        return credentialCipher.decrypt(profile, record.ciphertext)
    }

    fun registerTester(tester: NodeProfileConnectionTester) {
        testers[TesterKey(tester.chainUid, tester.protocol)] = tester
    }

    fun isAuthenticationSupported(profile: NodeProfile): Boolean =
        testers[TesterKey(profile.chainUid, profile.protocol)]
            ?.supportedAuthTypes
            ?.contains(profile.authType) == true

    fun isProfileSupported(profile: NodeProfile): Boolean = isAuthenticationSupported(profile)

    suspend fun testConnection(profile: NodeProfile): NodeProfileTestResult {
        return try {
            testConnection(profile, credentials(profile))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            NodeProfileTestResult.Unreachable
        }
    }

    suspend fun testConnection(profile: NodeProfile, credentials: NodeCredentials?): NodeProfileTestResult {
        return try {
            validate(profile)
            val tester = testers[TesterKey(profile.chainUid, profile.protocol)]
                ?: return NodeProfileTestResult.UnsupportedProtocol
            if (profile.authType !in tester.supportedAuthTypes) {
                return NodeProfileTestResult.AuthenticationUnsupported
            }
            tester.test(profile, credentials)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            NodeProfileTestResult.Unreachable
        }
    }

    private fun emitChange(profile: NodeProfile) {
        _profileUpdatedFlow.tryEmit(NodeProfileChange(profile.chainUid, profile.network, profile.profileId))
    }

    private fun validate(profile: NodeProfile) {
        require(profile.chainUid.isNotBlank()) { "Chain is required" }
        require(profile.network.isNotBlank()) { "Network is required" }
        require(profile.name.isNotBlank()) { "Node name is required" }
        require(profile.name.length <= MAX_NAME_LENGTH) { "Node name is too long" }

        val uri = try {
            URI(profile.endpoint.trim())
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid node endpoint")
        }
        val scheme = uri.scheme?.lowercase() ?: throw IllegalArgumentException("Endpoint scheme is required")
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("Endpoint host is required")
        require(uri.userInfo == null) { "Credentials must not be embedded in the endpoint URL" }
        require(uri.rawQuery == null && uri.rawFragment == null) { "Secrets must not be embedded in endpoint query or fragment" }
        if (profile.protocol != NodeProtocol.HTTP_JSON_RPC) {
            require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") { "Path components are not supported by this node protocol" }
        }
        require(uri.port == -1 || uri.port in 1..MAX_PORT) { "Endpoint port is invalid" }

        val onion = host.endsWith(".onion")
        val secureScheme = when (profile.protocol) {
            NodeProtocol.LIGHTWALLETD_GRPC,
            NodeProtocol.HTTP_JSON_RPC -> scheme == "https"
            NodeProtocol.ELECTRUM_JSON_RPC -> scheme == "ssl"
        }
        val plaintextScheme = when (profile.protocol) {
            NodeProtocol.LIGHTWALLETD_GRPC,
            NodeProtocol.HTTP_JSON_RPC -> scheme == "http"
            NodeProtocol.ELECTRUM_JSON_RPC -> scheme == "tcp"
        }

        when (profile.tlsMode) {
            NodeTlsMode.REQUIRED -> require(secureScheme) { "This node protocol requires TLS" }
            NodeTlsMode.DISABLED -> {
                require(plaintextScheme) { "Endpoint scheme does not match the selected protocol" }
                require(onion || host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "[::1]") {
                    "Plaintext node connections are allowed only for .onion or local endpoints"
                }
            }
        }
    }

    private fun validateCredentials(authType: NodeAuthType, credentials: NodeCredentials?) {
        val value = requireNotNull(credentials) { "Credentials are required" }
        when (authType) {
            NodeAuthType.NONE -> Unit
            NodeAuthType.BASIC -> {
                require(!value.username.isNullOrBlank()) { "Username is required" }
                require(!value.password.isNullOrBlank()) { "Password is required" }
                require(value.bearerToken.isNullOrEmpty()) { "Basic authentication cannot contain a bearer token" }
            }
            NodeAuthType.BEARER -> {
                require(!value.bearerToken.isNullOrBlank()) { "Bearer token is required" }
                require(value.username.isNullOrEmpty() && value.password.isNullOrEmpty()) {
                    "Bearer authentication cannot contain basic credentials"
                }
            }
        }
    }

    private data class TesterKey(val chainUid: String, val protocol: NodeProtocol)

    companion object {
        private const val MAX_NAME_LENGTH = 80
        private const val MAX_PORT = 65535
    }
}

data class NodeProfileChange(
    val chainUid: String,
    val network: String,
    val activeProfileId: String?,
)

interface NodeProfileConnectionTester {
    val chainUid: String
    val protocol: NodeProtocol
    val supportedAuthTypes: Set<NodeAuthType>

    suspend fun test(profile: NodeProfile, credentials: NodeCredentials?): NodeProfileTestResult
}

sealed interface NodeProfileTestResult {
    data class Reachable(val latencyMs: Long, val serverInfo: String? = null) : NodeProfileTestResult
    data object Unreachable : NodeProfileTestResult
    data object AuthenticationUnsupported : NodeProfileTestResult
    data object UnsupportedProtocol : NodeProfileTestResult
}
