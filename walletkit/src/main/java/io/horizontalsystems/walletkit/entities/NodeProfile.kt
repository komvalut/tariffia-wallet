package io.horizontalsystems.walletkit.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

enum class NodeProtocol {
    LIGHTWALLETD_GRPC,
    ELECTRUM_JSON_RPC,
    HTTP_JSON_RPC,
}

enum class NodeTlsMode {
    REQUIRED,
    DISABLED,
}

enum class NodeAuthType {
    NONE,
    BASIC,
    BEARER,
}

data class NodeProfile(
    val chainUid: String,
    val network: String,
    val profileId: String,
    val name: String,
    val endpoint: String,
    val protocol: NodeProtocol,
    val tlsMode: NodeTlsMode,
    val authType: NodeAuthType,
    val credentialRef: String?,
    val isSelected: Boolean,
    val isBuiltIn: Boolean = false,
)

data class NodeCredentials(
    val username: String? = null,
    val password: String? = null,
    val bearerToken: String? = null,
)

@Entity(
    tableName = "NodeProfileRecord",
    primaryKeys = ["chainUid", "network", "profileId"],
    indices = [
        Index(value = ["chainUid", "network"]),
        Index(value = ["credentialRef"], unique = true),
    ],
)
data class NodeProfileRecord(
    val chainUid: String,
    val network: String,
    val profileId: String,
    val name: String,
    val endpoint: String,
    val protocol: String,
    val tlsMode: String,
    val authType: String,
    val credentialRef: String?,
    val isSelected: Boolean,
    val isBuiltIn: Boolean,
) {
    fun toNodeProfile() = NodeProfile(
        chainUid = chainUid,
        network = network,
        profileId = profileId,
        name = name,
        endpoint = endpoint,
        protocol = NodeProtocol.valueOf(protocol),
        tlsMode = NodeTlsMode.valueOf(tlsMode),
        authType = NodeAuthType.valueOf(authType),
        credentialRef = credentialRef,
        isSelected = isSelected,
        isBuiltIn = isBuiltIn,
    )

    companion object {
        fun from(profile: NodeProfile) = NodeProfileRecord(
            chainUid = profile.chainUid,
            network = profile.network,
            profileId = profile.profileId,
            name = profile.name,
            endpoint = profile.endpoint,
            protocol = profile.protocol.name,
            tlsMode = profile.tlsMode.name,
            authType = profile.authType.name,
            credentialRef = profile.credentialRef,
            isSelected = profile.isSelected,
            isBuiltIn = profile.isBuiltIn,
        )
    }
}

@Entity(
    tableName = "NodeCredentialRecord",
    foreignKeys = [
        ForeignKey(
            entity = NodeProfileRecord::class,
            parentColumns = ["credentialRef"],
            childColumns = ["credentialRef"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["credentialRef"])],
)
data class NodeCredentialRecord(
    @androidx.room.PrimaryKey
    val credentialRef: String,
    val ciphertext: ByteArray,
)
