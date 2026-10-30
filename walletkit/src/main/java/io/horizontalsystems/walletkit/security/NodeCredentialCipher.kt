package io.horizontalsystems.walletkit.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.horizontalsystems.walletkit.entities.NodeCredentials
import io.horizontalsystems.walletkit.entities.NodeProfile
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class NodeCredentialCipher {
    @Synchronized
    fun encrypt(profile: NodeProfile, credentials: NodeCredentials): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(associatedData(profile))
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(serialize(credentials))

        return ByteBuffer.allocate(2 + iv.size + ciphertext.size)
            .put(FORMAT_VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
    }

    @Synchronized
    fun decrypt(profile: NodeProfile, ciphertext: ByteArray): NodeCredentials {
        require(ciphertext.size > HEADER_SIZE + GCM_TAG_BYTES) { "Invalid encrypted node credential" }
        val buffer = ByteBuffer.wrap(ciphertext)
        require(buffer.get() == FORMAT_VERSION) { "Unsupported encrypted node credential version" }
        val ivSize = buffer.get().toInt() and 0xff
        require(ivSize == GCM_IV_BYTES && buffer.remaining() > ivSize + GCM_TAG_BYTES) {
            "Invalid encrypted node credential envelope"
        }
        val iv = ByteArray(ivSize).also { buffer.get(it) }
        val encrypted = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(associatedData(profile))
        return deserialize(cipher.doFinal(encrypted))
    }

    private fun associatedData(profile: NodeProfile): ByteArray {
        val credentialRef = requireNotNull(profile.credentialRef) { "Profile has no credential reference" }
        return listOf(profile.chainUid, profile.network, profile.profileId, credentialRef)
            .joinToString(separator = "\u0000")
            .toByteArray(StandardCharsets.UTF_8)
    }

    private fun serialize(credentials: NodeCredentials): ByteArray = JSONObject()
        .put("username", credentials.username ?: JSONObject.NULL)
        .put("password", credentials.password ?: JSONObject.NULL)
        .put("bearerToken", credentials.bearerToken ?: JSONObject.NULL)
        .toString()
        .toByteArray(StandardCharsets.UTF_8)

    private fun deserialize(value: ByteArray): NodeCredentials {
        val json = JSONObject(String(value, StandardCharsets.UTF_8))
        return NodeCredentials(
            username = json.optString("username").takeUnless { json.isNull("username") },
            password = json.optString("password").takeUnless { json.isNull("password") },
            bearerToken = json.optString("bearerToken").takeUnless { json.isNull("bearerToken") },
        )
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "tariffia_node_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        const val HEADER_SIZE = 2
    }
}
