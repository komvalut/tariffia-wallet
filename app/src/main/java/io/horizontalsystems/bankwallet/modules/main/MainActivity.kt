package io.horizontalsystems.bankwallet.modules.main

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.horizontalsystems.walletkit.core.BaseActivity
import io.horizontalsystems.walletkit.core.StartupTrace
import io.horizontalsystems.walletkit.modules.nav3.EntryPage
import io.horizontalsystems.walletkit.modules.nav3.Nav3
import io.horizontalsystems.walletkit.ui.compose.ComposeAppTheme

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTrace.log("MainActivity.onCreate restored=${savedInstanceState != null}")
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            ComposeAppTheme {
                Nav3(EntryPage)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        StartupTrace.log("MainActivity.onResume")
    }

    override fun finish() {
        StartupTrace.log("MainActivity.finish() called", Throwable("finish() call site"))
        super.finish()
    }

    override fun onDestroy() {
        StartupTrace.log("MainActivity.onDestroy finishing=$isFinishing changingConfig=$isChangingConfigurations")
        super.onDestroy()
    }
}
