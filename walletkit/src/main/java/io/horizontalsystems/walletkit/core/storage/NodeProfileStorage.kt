package io.horizontalsystems.walletkit.core.storage

import io.horizontalsystems.walletkit.entities.NodeCredentialRecord
import io.horizontalsystems.walletkit.entities.NodeProfile
import io.horizontalsystems.walletkit.entities.NodeProfileRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NodeProfileStorage(private val appDatabase: AppDatabase) {
    private val dao by lazy { appDatabase.nodeProfileDao() }

    fun observeAll(): Flow<List<NodeProfile>> = dao.observeAll().map { records ->
        records.map(NodeProfileRecord::toNodeProfile)
    }

    fun getAll(): List<NodeProfile> = dao.getAll().map(NodeProfileRecord::toNodeProfile)

    fun getProfiles(chainUid: String, network: String): List<NodeProfile> =
        dao.getProfiles(chainUid, network).map(NodeProfileRecord::toNodeProfile)

    fun getProfile(chainUid: String, network: String, profileId: String): NodeProfile? =
        dao.getProfile(chainUid, network, profileId)?.toNodeProfile()

    fun getSelected(chainUid: String, network: String): NodeProfile? =
        dao.getSelected(chainUid, network)?.toNodeProfile()

    fun getCredential(credentialRef: String): NodeCredentialRecord? = dao.getCredential(credentialRef)

    fun save(profile: NodeProfile, ciphertext: ByteArray?, previousCredentialRef: String?) {
        appDatabase.runInTransaction {
            if (profile.isSelected) dao.clearSelection(profile.chainUid, profile.network)
            previousCredentialRef
                ?.takeIf { it != profile.credentialRef }
                ?.let(dao::deleteCredential)
            dao.insert(NodeProfileRecord.from(profile))
            profile.credentialRef?.let { credentialRef ->
                if (ciphertext == null) {
                    throw IllegalArgumentException("Encrypted credentials are required for credentialRef")
                }
                dao.insert(NodeCredentialRecord(credentialRef, ciphertext))
            }
        }
    }

    fun select(chainUid: String, network: String, profileId: String) {
        appDatabase.runInTransaction {
            dao.clearSelection(chainUid, network)
            if (dao.select(chainUid, network, profileId) != 1) {
                throw NoSuchElementException("Node profile not found")
            }
        }
    }

    fun clearSelection(chainUid: String, network: String) {
        appDatabase.runInTransaction { dao.clearSelection(chainUid, network) }
    }

    fun delete(chainUid: String, network: String, profileId: String) {
        appDatabase.runInTransaction { dao.delete(chainUid, network, profileId) }
    }
}
