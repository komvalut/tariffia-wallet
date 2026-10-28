package io.horizontalsystems.walletkit.modules.send.v2

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.horizontalsystems.walletkit.entities.Address
import io.horizontalsystems.walletkit.entities.Wallet
import io.horizontalsystems.walletkit.modules.nav3.HSNavigation
import io.horizontalsystems.walletkit.modules.nav3.HSPage
import io.horizontalsystems.walletkit.serializers.BigDecimalSerializer
import io.horizontalsystems.walletkit.serializers.HSScreenKClassSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import kotlin.reflect.KClass

/**
 * Send screen for every blockchain type. After a successful send the flow pops back to and
 * including [sendEntryPointDestId], or this page when none is given. [purpose] carries the
 * inputs for a plain transfer, possibly seeded from a payment link, and offers every tab.
 */
@Serializable
data class SendV2Page(
    val wallet: Wallet,
    @Serializable(with = HSScreenKClassSerializer::class) val sendEntryPointDestId: KClass<out HSPage>? = null,
    val purpose: Purpose = Purpose.Transfer(),
) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = viewModel<SendViewModel>(factory = SendViewModel.Factory(wallet))
        SendV2Screen(
            navigation = navigation,
            viewModel = viewModel,
            sendEntryPointDestId = sendEntryPointDestId ?: SendV2Page::class,
            purpose = purpose,
        )
    }

    /** What the send is for; each purpose carries its inputs and implies how the screen is presented. */
    @Serializable
    sealed class Purpose {
        /**
         * The user picks the recipient and every tab is offered. [prefill] seeds the form
         * from a payment link, its address already confirmed on [SendRecipientPage].
         */
        @Serializable
        data class Transfer(val prefill: Prefill? = null) : Purpose()
    }

    /**
     * Values a payment link supplies for a [Purpose.Transfer]; [address] has passed the
     * address checks, and [contactName] names the contact it belongs to, if any.
     */
    @Serializable
    data class Prefill(
        val address: Address? = null,
        val riskyAddress: Boolean = false,
        val contactName: String? = null,
        @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal? = null,
        val memo: String? = null,
    )
}
