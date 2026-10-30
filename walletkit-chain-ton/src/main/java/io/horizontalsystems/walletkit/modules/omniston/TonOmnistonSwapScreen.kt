package io.horizontalsystems.walletkit.modules.omniston

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.managers.OmnistonQuote
import io.horizontalsystems.walletkit.modules.nav3.HSNavigation
import io.horizontalsystems.walletkit.modules.multiswap.history.SwapInfoPage
import io.horizontalsystems.walletkit.modules.multiswap.history.SwapHistoryPage
import io.horizontalsystems.walletkit.ui.compose.ComposeAppTheme
import io.horizontalsystems.walletkit.ui.compose.components.body_leah
import io.horizontalsystems.walletkit.ui.compose.components.VSpacer
import io.horizontalsystems.walletkit.uiv3.components.HSScaffold
import io.horizontalsystems.walletkit.uiv3.components.controls.ButtonVariant
import io.horizontalsystems.walletkit.uiv3.components.controls.HSButton
import io.horizontalsystems.walletkit.uiv3.components.controls.TokenSelector
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

@Composable
fun TonOmnistonSwapScreen(initialToken: Token, navigation: HSNavigation) {
    val viewModel = viewModel<TonOmnistonSwapViewModel>(initializer = {
        TonOmnistonSwapViewModel(initialToken)
    })
    val state = viewModel.state

    HSScaffold(
        title = stringResource(R.string.Swap),
        onBack = navigation::removeLastOrNull,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (state.review == null) {
                AssetDropdown(
                    label = stringResource(R.string.Swap_FromAmountTitle),
                    options = state.assets,
                    selected = state.inputToken,
                    enabled = !state.working,
                    onSelect = viewModel::setInputToken,
                )
                VSpacer(12.dp)
                androidx.compose.material3.OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.inputAmount,
                    onValueChange = viewModel::setInputAmount,
                    label = { Text(stringResource(R.string.Swap_EnterAmount)) },
                    singleLine = true,
                    enabled = !state.working,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                AvailableBalance(state.assets.firstOrNull { it.token == state.inputToken })
                VSpacer(20.dp)
                AssetDropdown(
                    label = stringResource(R.string.Swap_ToAmountTitle),
                    options = state.assets.filter { it.token != state.inputToken },
                    selected = state.outputToken,
                    enabled = !state.working,
                    onSelect = viewModel::setOutputToken,
                )
                state.quote?.let { quote ->
                    VSpacer(20.dp)
                    QuoteDetails(quote, state.inputToken, state.outputToken)
                }
            } else {
                ReviewDetails(state.review, state.inputToken, state.outputToken)
            }

            state.error?.let {
                VSpacer(12.dp)
                Text(text = it, color = ComposeAppTheme.colors.jacob)
            }
            state.tradeStatus?.let {
                VSpacer(16.dp)
                Text(text = statusText(it), color = ComposeAppTheme.colors.leah)
            }

            VSpacer(24.dp)
            var buttonEnabled by remember { mutableStateOf(true) }
            LaunchedEffect(state.quoteLoading, state.working, state.quote?.quoteId, state.review, state.error, state.tradeStatus) {
                buttonEnabled = !state.quoteLoading && !state.working && state.tradeStatus == null
            }
            val buttonLabel = when {
                state.working -> stringResource(R.string.Swap_Swapping)
                state.quoteLoading -> stringResource(R.string.Swap_Quoting)
                state.review != null -> stringResource(R.string.Button_Confirm)
                else -> stringResource(R.string.Swap_Proceed)
            }
            HSButton(
                modifier = Modifier.fillMaxWidth(),
                title = buttonLabel,
                enabled = buttonEnabled && !state.working && !state.quoteLoading && state.tradeStatus == null,
                onClick = navigation.authorizedAction {
                    if (buttonEnabled) {
                        buttonEnabled = false
                        when {
                            state.review != null -> viewModel.confirmSwap()
                            state.quote != null -> viewModel.previewSwap()
                            else -> viewModel.requestQuote()
                        }
                    }
                },
            )
            if (state.review != null && state.tradeStatus == null) {
                VSpacer(8.dp)
                HSButton(
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.Button_Back),
                    enabled = !state.working,
                    onClick = viewModel::cancelReview,
                )
            }
            state.swapRecordId?.let { recordId ->
                VSpacer(8.dp)
                HSButton(
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.Button_View),
                    onClick = navigation.authorizedAction {
                        navigation.slideFromRight(SwapInfoPage(SwapInfoPage.Input(recordId)))
                    },
                )
            }
            VSpacer(8.dp)
            HSButton(
                variant = ButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.SwapHistory_Title),
                onClick = navigation.authorizedAction {
                    navigation.slideFromRight(SwapHistoryPage)
                },
            )
            VSpacer(24.dp)
        }
    }
}

@Composable
private fun AssetDropdown(
    label: String,
    options: List<TonSwapAssetOption>,
    selected: Token?,
    enabled: Boolean,
    onSelect: (Token) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(text = label, style = ComposeAppTheme.typography.caption, color = ComposeAppTheme.colors.grey)
        VSpacer(8.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { expanded = true }
                .padding(vertical = 8.dp),
        ) {
            TokenSelector(token = selected, onClick = if (enabled) ({ expanded = true }) else null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(onClick = {
                    expanded = false
                    onSelect(option.token)
                }) {
                    Column {
                        Text(option.token.coin.code)
                        Text(
                            text = App.numberFormatter.formatCoinFull(
                                option.balance,
                                option.token.coin.code,
                                option.token.decimals,
                            ),
                            style = ComposeAppTheme.typography.caption,
                            color = ComposeAppTheme.colors.grey,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailableBalance(option: TonSwapAssetOption?) {
    if (option != null) {
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = stringResource(
                R.string.Send_Available,
                App.numberFormatter.formatCoinFull(option.balance, option.token.coin.code, option.token.decimals),
            ),
            style = ComposeAppTheme.typography.caption,
            color = ComposeAppTheme.colors.grey,
        )
    }
}

@Composable
private fun QuoteDetails(quote: OmnistonQuote, inputToken: Token?, outputToken: Token?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailRow(
            stringResource(R.string.Swap_ToAmountTitle),
            formatUnits(quote.outputUnits, outputToken?.decimals ?: 0, outputToken?.coin?.code),
        )
        DetailRow(
            stringResource(R.string.Swap_MinimumReceived),
            formatUnits(quote.minimumOutputUnits, outputToken?.decimals ?: 0, outputToken?.coin?.code),
        )
        quote.priceImpactPips?.let {
            val percent = BigDecimal(it).divide(BigDecimal(10_000), 4, RoundingMode.HALF_UP).stripTrailingZeros()
            DetailRow(stringResource(R.string.Swap_PriceImpact), "$percent%")
        }
        if (quote.route.isNotBlank()) DetailRow(stringResource(R.string.Swap_Providers), quote.route)
        DetailRow(
            stringResource(R.string.Swap_Slippage),
            "${BigDecimal(DEFAULT_SLIPPAGE_PIPS).divide(BigDecimal(10_000)).stripTrailingZeros()}%",
        )
        if (quote.protocolFeeUnits.signum() > 0) {
            DetailRow(
                "Protocol fee",
                formatUnits(quote.protocolFeeUnits, outputToken?.decimals ?: 0, outputToken?.coin?.code),
            )
        }
        if (quote.integratorFeeUnits.signum() > 0) {
            DetailRow(
                "Integrator fee",
                formatUnits(quote.integratorFeeUnits, outputToken?.decimals ?: 0, outputToken?.coin?.code),
            )
        }
        inputToken?.let { Text(text = "${it.coin.code} → ${outputToken?.coin?.code.orEmpty()}") }
    }
}

@Composable
private fun ReviewDetails(review: TonSwapReview, inputToken: Token?, outputToken: Token?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailRow(
            stringResource(R.string.Swap_FromAmountTitle),
            "${formatUnits(review.quote.inputUnits, inputToken?.decimals ?: 0, inputToken?.coin?.code)}",
        )
        DetailRow(
            stringResource(R.string.Swap_ToAmountTitle),
            formatUnits(review.quote.outputUnits, outputToken?.decimals ?: 0, outputToken?.coin?.code),
        )
        DetailRow(
            stringResource(R.string.Swap_MinimumReceived),
            formatUnits(review.quote.minimumOutputUnits, outputToken?.decimals ?: 0, outputToken?.coin?.code),
        )
        DetailRow(
            stringResource(R.string.FeeSettings_NetworkFee),
            App.numberFormatter.formatCoinFull(review.fee, "TON", 9),
        )
        DetailRow(stringResource(R.string.Swap_Providers), "${review.messageCount} TON message(s)")
        review.quote.priceImpactPips?.let {
            val percent = BigDecimal(it).divide(BigDecimal(10_000), 4, RoundingMode.HALF_UP).stripTrailingZeros()
            DetailRow(stringResource(R.string.Swap_PriceImpact), "$percent%")
        }
    }
}

@Composable
private fun DetailRow(title: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        body_leah(text = title)
        Spacer(Modifier.padding(start = 8.dp))
        Text(text = value, color = ComposeAppTheme.colors.leah)
    }
}

@Composable
private fun statusText(status: String): String = when (status) {
    "TRADE_STATUS_FULLY_FILLED" -> stringResource(R.string.Hud_Text_Done)
    "TRADE_STATUS_PARTIALLY_FILLED" -> "Partially completed"
    "TRADE_STATUS_FAILED" -> stringResource(R.string.SwapInfo_StatusFailed)
    "TRADE_STATUS_CANCELLED" -> "Cancelled"
    "AWAITING_TRANSFER" -> stringResource(R.string.Swap_Swapping)
    "SUBMITTED" -> stringResource(R.string.Swap_Swapping)
    "SUBMISSION_UNKNOWN" -> "Submission status unknown; tracking continues"
    else -> status.removePrefix("TRADE_STATUS_").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

private fun formatUnits(units: BigInteger, decimals: Int, code: String?): String =
    App.numberFormatter.formatCoinFull(BigDecimal(units, decimals), code, decimals)

private const val DEFAULT_SLIPPAGE_PIPS = 10_000
