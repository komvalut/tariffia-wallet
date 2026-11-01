package io.horizontalsystems.bankwallet.core.managers

import io.horizontalsystems.walletkit.ISystemInfoManager
import io.horizontalsystems.walletkit.core.ILocalStorage
import io.horizontalsystems.walletkit.core.managers.ReleaseNotesManager
import io.horizontalsystems.walletkit.core.providers.IAppConfigProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

// GitHub release tags are v-prefixed ("v1.0.0") while the Android versionName
// is normalized to "1.0.0", so the URL must re-add the leading v.
class ReleaseNotesManagerTest {

    @Test
    fun releaseNotesUrlUsesTagWithV() {
        assertEquals(
            "https://api.github.com/repos/komvalut/tariffia-wallet/releases/tags/v1.0.0",
            managerFor(appVersion = "1.0.0").releaseNotesUrl,
        )
    }

    // Existing versionForUrl behavior: notes live on X.Y.0 tags only
    @Test
    fun releaseNotesUrlNormalizesPatch() {
        assertEquals(
            "https://api.github.com/repos/komvalut/tariffia-wallet/releases/tags/v1.2.0",
            managerFor(appVersion = "1.2.3").releaseNotesUrl,
        )
    }

    private fun managerFor(appVersion: String): ReleaseNotesManager {
        val systemInfoManager = Mockito.mock(ISystemInfoManager::class.java)
        Mockito.`when`(systemInfoManager.appVersion).thenReturn(appVersion)
        val localStorage = Mockito.mock(ILocalStorage::class.java)
        val appConfigProvider = Mockito.mock(IAppConfigProvider::class.java)
        Mockito.`when`(appConfigProvider.releaseNotesUrl)
            .thenReturn("https://api.github.com/repos/komvalut/tariffia-wallet/releases/tags/")
        return ReleaseNotesManager(systemInfoManager, localStorage, appConfigProvider)
    }
}
