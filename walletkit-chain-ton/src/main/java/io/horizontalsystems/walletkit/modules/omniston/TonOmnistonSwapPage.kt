package io.horizontalsystems.walletkit.modules.omniston

import androidx.compose.runtime.Composable
import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.walletkit.modules.nav3.HSNavigation
import io.horizontalsystems.walletkit.modules.nav3.HSPage
import kotlinx.serialization.Serializable

@Serializable
data class TonOmnistonSwapPage(val token: Token) : HSPage() {
    override fun contentKey() = "${super.contentKey()}-${token.coin.uid}-${token.type}"

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        TonOmnistonSwapScreen(token, navigation)
    }
}
