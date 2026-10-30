package io.horizontalsystems.walletkit.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_87_88 : Migration(87, 88) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `NodeProfileRecord` (" +
                "`chainUid` TEXT NOT NULL, `network` TEXT NOT NULL, `profileId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `endpoint` TEXT NOT NULL, `protocol` TEXT NOT NULL, " +
                "`tlsMode` TEXT NOT NULL, `authType` TEXT NOT NULL, `credentialRef` TEXT, " +
                "`isSelected` INTEGER NOT NULL, `isBuiltIn` INTEGER NOT NULL, " +
                "PRIMARY KEY(`chainUid`, `network`, `profileId`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_NodeProfileRecord_chainUid_network` ON `NodeProfileRecord` (`chainUid`, `network`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_NodeProfileRecord_credentialRef` ON `NodeProfileRecord` (`credentialRef`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `NodeCredentialRecord` (" +
                "`credentialRef` TEXT NOT NULL, `ciphertext` BLOB NOT NULL, PRIMARY KEY(`credentialRef`), " +
                "FOREIGN KEY(`credentialRef`) REFERENCES `NodeProfileRecord`(`credentialRef`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_NodeCredentialRecord_credentialRef` ON `NodeCredentialRecord` (`credentialRef`)")
        db.execSQL(
            "INSERT OR IGNORE INTO `NodeProfileRecord` " +
                "(`chainUid`, `network`, `profileId`, `name`, `endpoint`, `protocol`, `tlsMode`, `authType`, `credentialRef`, `isSelected`, `isBuiltIn`) " +
                "SELECT 'zcash', 'mainnet', lower(hex(randomblob(16))), `url`, `url`, 'LIGHTWALLETD_GRPC', 'REQUIRED', 'NONE', NULL, " +
                "CASE WHEN EXISTS (SELECT 1 FROM `BlockchainSettingRecord` WHERE `blockchainUid` = 'zcash' " +
                "AND `key` = 'zcash-endpoint' AND `value` = `ZcashEndpointRecord`.`url`) THEN 1 ELSE 0 END, 0 " +
                "FROM `ZcashEndpointRecord` WHERE `url` LIKE 'https://%' AND instr(substr(`url`, 9), '/') = 0 " +
                "AND instr(`url`, '@') = 0 AND instr(`url`, '?') = 0 AND instr(`url`, '#') = 0",
        )
        db.execSQL(
            "DELETE FROM `ZcashEndpointRecord` WHERE `url` NOT LIKE 'https://%' OR instr(substr(`url`, 9), '/') > 0 " +
                "OR instr(`url`, '@') > 0 OR instr(`url`, '?') > 0 OR instr(`url`, '#') > 0",
        )
        db.execSQL(
            "UPDATE `BlockchainSettingRecord` SET `value` = 'https://zec.rocks:443' " +
                "WHERE `blockchainUid` = 'zcash' AND `key` = 'zcash-endpoint' AND " +
                "(`value` NOT LIKE 'https://%' OR instr(substr(`value`, 9), '/') > 0 OR " +
                "instr(`value`, '@') > 0 OR instr(`value`, '?') > 0 OR instr(`value`, '#') > 0)",
        )
    }
}
