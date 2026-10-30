package io.horizontalsystems.walletkit.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.horizontalsystems.walletkit.entities.NodeCredentialRecord
import io.horizontalsystems.walletkit.entities.NodeProfileRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeProfileDao {
    @Query("SELECT * FROM NodeProfileRecord ORDER BY chainUid, network, name")
    fun observeAll(): Flow<List<NodeProfileRecord>>

    @Query("SELECT * FROM NodeProfileRecord ORDER BY chainUid, network, name")
    fun getAll(): List<NodeProfileRecord>

    @Query("SELECT * FROM NodeProfileRecord WHERE chainUid = :chainUid AND network = :network ORDER BY name")
    fun getProfiles(chainUid: String, network: String): List<NodeProfileRecord>

    @Query("SELECT * FROM NodeProfileRecord WHERE chainUid = :chainUid AND network = :network AND profileId = :profileId LIMIT 1")
    fun getProfile(chainUid: String, network: String, profileId: String): NodeProfileRecord?

    @Query("SELECT * FROM NodeProfileRecord WHERE chainUid = :chainUid AND network = :network AND isSelected = 1 LIMIT 1")
    fun getSelected(chainUid: String, network: String): NodeProfileRecord?

    @Query("SELECT * FROM NodeCredentialRecord WHERE credentialRef = :credentialRef LIMIT 1")
    fun getCredential(credentialRef: String): NodeCredentialRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(profile: NodeProfileRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(credential: NodeCredentialRecord)

    @Query("UPDATE NodeProfileRecord SET isSelected = 0 WHERE chainUid = :chainUid AND network = :network")
    fun clearSelection(chainUid: String, network: String)

    @Query("UPDATE NodeProfileRecord SET isSelected = 1 WHERE chainUid = :chainUid AND network = :network AND profileId = :profileId")
    fun select(chainUid: String, network: String, profileId: String): Int

    @Query("DELETE FROM NodeProfileRecord WHERE chainUid = :chainUid AND network = :network AND profileId = :profileId")
    fun delete(chainUid: String, network: String, profileId: String)

    @Query("DELETE FROM NodeCredentialRecord WHERE credentialRef = :credentialRef")
    fun deleteCredential(credentialRef: String)
}
