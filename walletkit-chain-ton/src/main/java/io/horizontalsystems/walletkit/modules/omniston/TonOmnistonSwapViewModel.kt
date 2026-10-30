package io.horizontalsystems.walletkit.modules.omniston

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.marketkit.models.TokenQuery
import io.horizontalsystems.marketkit.models.TokenType
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.ISendTonAdapter
import io.horizontalsystems.walletkit.core.badge
import io.horizontalsystems.walletkit.core.adapters.TonTransactionConverter
import io.horizontalsystems.walletkit.core.chain.ChainRegistry
import io.horizontalsystems.walletkit.chain.ton.TonChainPlugin
import io.horizontalsystems.walletkit.core.managers.OmnistonQuote
import io.horizontalsystems.walletkit.core.managers.OmnistonQuoteEvent
import io.horizontalsystems.walletkit.core.managers.OmnistonRepository
import io.horizontalsystems.walletkit.core.managers.TonKitWrapper
import io.horizontalsystems.walletkit.core.managers.toTonWalletFullAccess
import io.horizontalsystems.walletkit.core.meta
import io.horizontalsystems.walletkit.entities.Account
import io.horizontalsystems.walletkit.entities.SwapRecord
import io.horizontalsystems.walletkit.entities.Wallet
import io.horizontalsystems.walletkit.modules.multiswap.history.SwapStatus
import io.horizontalsystems.walletkit.modules.transactions.TransactionSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.ton.boc.BagOfCells
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

data class TonSwapAssetOption(
    val token: Token,
    val wallet: Wallet,
    val balance: BigDecimal,
    val asset: io.horizontalsystems.walletkit.core.managers.TonSwapAsset,
)

data class TonSwapReview(
    val quote: OmnistonQuote,
    val fee: BigDecimal,
    val messageCount: Int,
)

data class TonOmnistonSwapUiState(
    val assets: List<TonSwapAssetOption> = emptyList(),
    val inputToken: Token? = null,
    val outputToken: Token? = null,
    val inputAmount: String = "",
    val quote: OmnistonQuote? = null,
    val quoteLoading: Boolean = false,
    val working: Boolean = false,
    val review: TonSwapReview? = null,
    val swapRecordId: Int? = null,
    val tradeStatus: String? = null,
    val error: String? = null,
)

class TonOmnistonSwapViewModel(initialToken: Token) : ViewModel() {
    private val plugin = ChainRegistry[BlockchainType.Ton] as? TonChainPlugin
    private val repository: OmnistonRepository? = plugin?.omnistonRepository
    private val account: Account? = App.accountManager.activeAccount
    private var tonKitWrapper: TonKitWrapper? = null
    private var kitManagerLinked = false
    private var quoteJob: Job? = null
    private var recordStatusJob: Job? = null
    private var sendRequest: SendRequestEntity? = null
    private var selectedMessages = emptyList<io.horizontalsystems.walletkit.core.managers.OmnistonTonMessage>()
    private var selectedQuote: OmnistonQuote? = null

    var state by mutableStateOf(TonOmnistonSwapUiState())
        private set

    init {
        val currentAccount = account
        if (currentAccount == null) {
            state = state.copy(error = "No active TON account")
        } else {
            val wrapper = try {
                currentAccount.type.toTonWalletFullAccess()
                requireNotNull(plugin).kitManager.getTonKitWrapper(currentAccount)
            } catch (e: Exception) {
                null
            }

            if (wrapper == null) {
                state = state.copy(error = "A full-access TON wallet is required")
            } else {
                tonKitWrapper = wrapper
                kitManagerLinked = true

                val assets = App.walletManager.activeWallets
                    .filter { it.account.id == currentAccount.id && it.token.blockchainType == BlockchainType.Ton }
                    .mapNotNull(::assetOption)
                    .distinctBy { it.token }

                val input = assets.firstOrNull { it.token == initialToken }
                    ?: assets.firstOrNull { it.token.type == TokenType.Native }
                val output = assets.firstOrNull { it.token != input?.token && it.token.type == TokenType.Native }
                    ?: assets.firstOrNull { it.token != input?.token }
                state = state.copy(assets = assets, inputToken = input?.token, outputToken = output?.token)
                if (assets.isEmpty()) state = state.copy(error = "No enabled TON assets are available")
            }
        }
    }

    fun setInputToken(token: Token) {
        if (state.working || state.tradeStatus != null) return
        if (token == state.outputToken) {
            val oldInput = state.inputToken
            state = state.copy(inputToken = token, outputToken = oldInput)
        } else {
            state = state.copy(inputToken = token)
        }
        clearQuoteAndReview()
    }

    fun setOutputToken(token: Token) {
        if (state.working || state.tradeStatus != null) return
        if (token == state.inputToken) {
            val oldOutput = state.outputToken
            state = state.copy(outputToken = token, inputToken = oldOutput)
        } else {
            state = state.copy(outputToken = token)
        }
        clearQuoteAndReview()
    }

    fun setInputAmount(value: String) {
        if (state.working || state.tradeStatus != null) return
        state = state.copy(inputAmount = value, error = null)
        clearQuoteAndReview()
    }

    fun requestQuote() {
        val repository = repository ?: return fail("Omniston is unavailable")
        val input = selectedAsset(state.inputToken) ?: return fail("Select an enabled input asset")
        val output = selectedAsset(state.outputToken) ?: return fail("Select an enabled output asset")
        val units = amountUnits(state.inputAmount, input.token.decimals)
            ?: return fail("Enter a valid amount with no more than ${input.token.decimals} decimal places")
        if (units.signum() <= 0) return fail("Amount must be greater than zero")

        val freshBalance = freshBalanceUnits(input)
        if (freshBalance == null || units > freshBalance) return fail("Input amount exceeds available balance")

        quoteJob?.cancel()
        sendRequest = null
        selectedMessages = emptyList()
        selectedQuote = null
        state = state.copy(quote = null, quoteLoading = true, review = null, tradeStatus = null, error = null)

        quoteJob = viewModelScope.launch {
            try {
                repository.requestQuotes(
                    inputAsset = input.asset.copy(balanceUnits = freshBalance),
                    outputAsset = output.asset,
                    inputUnits = units,
                    maxSlippagePips = DEFAULT_SLIPPAGE_PIPS,
                ).collect { event ->
                    when (event) {
                        OmnistonQuoteEvent.Acknowledged -> Unit
                        OmnistonQuoteEvent.NoQuote -> {
                            selectedQuote = null
                            state = state.copy(quote = null, quoteLoading = false)
                        }
                        is OmnistonQuoteEvent.Updated -> {
                            if (
                                event.quote.inputAssetKey != assetKey(input.asset) ||
                                event.quote.outputAssetKey != assetKey(output.asset)
                            ) {
                                selectedQuote = null
                                state = state.copy(
                                    quote = null,
                                    quoteLoading = false,
                                    error = "Quote assets do not match the selected tokens",
                                )
                            } else {
                                selectedQuote = event.quote
                                state = state.copy(quote = event.quote, quoteLoading = false)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                state = state.copy(quote = null, quoteLoading = false, error = e.message ?: "Quote request failed")
            }
        }
    }

    fun previewSwap() {
        val repository = repository ?: return fail("Omniston is unavailable")
        val quote = selectedQuote ?: return fail("Request a current quote first")
        quoteJob?.cancel()
        val input = selectedAsset(state.inputToken) ?: return fail("Select an input asset")
        val currentAccount = account ?: return fail("No active TON account")
        if (App.accountManager.activeAccount?.id != currentAccount.id) return fail("Active account changed")

        val units = amountUnits(state.inputAmount, input.token.decimals)
            ?: return fail("Enter a valid amount")
        val freshBalance = freshBalanceUnits(input)
        if (freshBalance == null || units > freshBalance) return fail("Input balance changed")
        if (units != quote.inputUnits) return fail("Quote amount changed; request a new quote")

        state = state.copy(working = true, error = null, review = null)
        viewModelScope.launch {
            try {
                val walletAddress = requireNotNull(plugin).kitManager.getAddress(currentAccount.type)
                val messages = repository.buildTonSwap(quote.quoteId, walletAddress)
                val request = createSendRequest(messages)
                val wrapper = requireNotNull(tonKitWrapper)
                val event = wrapper.tonKit.getDetails(request)
                val baseToken = App.coinManager.getToken(TokenQuery(BlockchainType.Ton, TokenType.Native))
                    ?: throw IllegalStateException("TON token is unavailable")
                val source = TransactionSource(baseToken.blockchain, currentAccount, baseToken.type.meta)
                val record = TonTransactionConverter(
                    wrapper.tonKit.receiveAddress,
                    App.coinManager,
                    source,
                    baseToken,
                ).createTransactionRecord(event)

                validateNativeFunds(messages, record.fee.decimalValue ?: BigDecimal.ZERO)
                sendRequest = request
                selectedMessages = messages
                selectedQuote = quote
                state = state.copy(
                    working = false,
                    review = TonSwapReview(quote, record.fee.decimalValue ?: BigDecimal.ZERO, messages.size),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                state = state.copy(working = false, error = e.message ?: "Could not build swap preview")
            }
        }
    }

    fun confirmSwap() {
        if (state.working || sendRequest == null) return
        val currentAccount = account ?: return fail("No active TON account")
        if (App.accountManager.activeAccount?.id != currentAccount.id) return fail("Active account changed")
        val input = selectedAsset(state.inputToken) ?: return fail("Input asset is unavailable")
        val output = selectedAsset(state.outputToken) ?: return fail("Output asset is unavailable")
        val amount = amountUnits(state.inputAmount, input.token.decimals) ?: return fail("Invalid input amount")
        val balance = freshBalanceUnits(input)
        if (balance == null || amount > balance) return fail("Input balance changed")

        val adapter = App.adapterManager.getAdapterForToken<ISendTonAdapter>(input.token)
            ?: return fail("TON wallet is unavailable")
        val request = requireNotNull(sendRequest)
        if (request.validUntil <= System.currentTimeMillis() / 1000) {
            return fail("Swap preview expired; request a new quote")
        }
        val review = state.review ?: return fail("Review the swap before confirming")
        val quote = selectedQuote ?: return fail("Accepted quote is unavailable")
        try {
            validateNativeFunds(selectedMessages, review.fee)
        } catch (e: Exception) {
            return fail(e.message ?: "Insufficient TON for network fees")
        }
        state = state.copy(working = true, error = null)

        viewModelScope.launch {
            var recordId: Int? = null
            try {
                val boc = adapter.sign(request)
                val query = bocHash(boc)
                val walletAddress = requireNotNull(plugin).kitManager.getAddress(currentAccount.type)
                val savedRecordId = saveSwapRecord(input, output, quote, currentAccount, walletAddress, query)
                recordId = savedRecordId
                state = state.copy(swapRecordId = savedRecordId)
                observePersistentStatus(savedRecordId)
                OmnistonTonSwapTrackingWorker.enqueue(App.instance, savedRecordId)
                try {
                    adapter.send(boc)
                    state = state.copy(working = false, tradeStatus = "SUBMITTED")
                } catch (sendError: Throwable) {
                    state = state.copy(working = false, tradeStatus = "SUBMISSION_UNKNOWN", error = sendError.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                state = state.copy(working = false, error = e.message ?: "Swap signing failed")
                // Once a tracking record exists, WorkManager owns retries across page/process loss.
                if (recordId != null) {
                    state = state.copy(swapRecordId = recordId, tradeStatus = "SUBMISSION_UNKNOWN")
                }
            }
        }
    }

    fun cancelReview() {
        sendRequest = null
        selectedMessages = emptyList()
        state = state.copy(review = null, error = null)
    }

    private fun saveSwapRecord(
        input: TonSwapAssetOption,
        output: TonSwapAssetOption,
        quote: OmnistonQuote,
        account: Account,
        traderAddress: String,
        outgoingTxQuery: String,
    ): Int {
        val amountIn = BigDecimal(state.inputAmount)
            .setScale(input.token.decimals, RoundingMode.UNNECESSARY)
            .stripTrailingZeros()
            .toPlainString()
        val amountOut = BigDecimal(quote.outputUnits, output.token.decimals).stripTrailingZeros().toPlainString()
        val minimumOutput = BigDecimal(quote.minimumOutputUnits, output.token.decimals)
            .stripTrailingZeros()
            .toPlainString()
        val trackingHandle = JSONObject()
            .put("quoteId", quote.quoteId)
            .put("traderAddress", traderAddress)
            .put("outgoingTxQuery", outgoingTxQuery)
            .put("outputDecimals", output.token.decimals)
            .toString()
        return App.swapRecordManager.save(
            SwapRecord(
                accountId = account.id,
                timestamp = System.currentTimeMillis(),
                providerId = OmnistonTonSwapTrackingWorker.PROVIDER_ID,
                providerName = "STON.fi Omniston",
                tokenInUid = input.token.tokenQuery.id,
                tokenInCoinCode = input.token.coin.code,
                tokenInCoinUid = input.token.coin.uid,
                tokenInBadge = input.token.badge,
                tokenInBlockchainTypeUid = input.token.blockchainType.uid,
                tokenOutUid = output.token.tokenQuery.id,
                tokenOutCoinCode = output.token.coin.code,
                tokenOutCoinUid = output.token.coin.uid,
                tokenOutBadge = output.token.badge,
                tokenOutBlockchainTypeUid = output.token.blockchainType.uid,
                amountIn = amountIn,
                amountOut = amountOut,
                amountOutMin = minimumOutput,
                recipientAddress = traderAddress,
                sourceAddress = traderAddress,
                transactionHash = null,
                providerSwapId = quote.quoteId,
                fromAsset = assetKey(input.asset),
                toAsset = assetKey(output.asset),
                depositAddress = null,
                status = SwapStatus.Depositing.name,
                trackingHandle = trackingHandle,
            ),
        )
    }

    private fun observePersistentStatus(recordId: Int) {
        recordStatusJob?.cancel()
        recordStatusJob = viewModelScope.launch {
            App.swapRecordManager.recordsUpdatedFlow.collect {
                val record = App.swapRecordManager.getById(recordId) ?: return@collect
                val status = runCatching { SwapStatus.valueOf(record.status) }.getOrNull() ?: return@collect
                val tradeStatus = when (status) {
                    SwapStatus.Depositing -> "AWAITING_TRANSFER"
                    SwapStatus.Swapping, SwapStatus.Sending -> "TRADE_STATUS_IN_PROGRESS"
                    SwapStatus.Completed -> "TRADE_STATUS_FULLY_FILLED"
                    SwapStatus.Failed, SwapStatus.ActionRequired, SwapStatus.Expired, SwapStatus.Refunded -> "TRADE_STATUS_FAILED"
                }
                state = state.copy(tradeStatus = tradeStatus)
            }
        }
    }

    private fun createSendRequest(messages: List<io.horizontalsystems.walletkit.core.managers.OmnistonTonMessage>): SendRequestEntity {
        val jsonMessages = JSONArray()
        messages.forEach { message ->
            jsonMessages.put(
                JSONObject()
                    .put("address", message.targetAddress)
                    .put("amount", message.sendAmount.toString())
                    .put("payload", message.payloadHex.hexToBase64())
                    .apply { message.stateInitHex?.let { put("stateInit", it.hexToBase64()) } },
            )
        }
        val data = JSONObject()
            .put("valid_until", System.currentTimeMillis() / 1000 + TRANSACTION_TTL_SECONDS)
            .put("network", TonNetwork.MAINNET.value)
            .put("messages", jsonMessages)
        return SendRequestEntity(data, tonConnectRequestId = "", dAppId = "")
    }

    private fun validateNativeFunds(
        messages: List<io.horizontalsystems.walletkit.core.managers.OmnistonTonMessage>,
        estimatedFee: BigDecimal,
    ) {
        val nativeOption = state.assets.firstOrNull { it.token.type == TokenType.Native }
            ?: throw IllegalStateException("TON gas wallet is unavailable")
        val nativeBalance = freshBalanceUnits(nativeOption)
            ?: throw IllegalStateException("TON balance is unavailable")
        val messageValue = messages.fold(BigInteger.ZERO) { total, message ->
            total + BigInteger.valueOf(message.sendAmount)
        }
        val feeUnits = estimatedFee.movePointRight(TON_DECIMALS).setScale(0, RoundingMode.CEILING).toBigInteger()
        if (nativeBalance < messageValue + feeUnits) {
            throw IllegalStateException("Insufficient TON for swap messages and network fee")
        }
    }

    private fun assetOption(wallet: Wallet): TonSwapAssetOption? {
        val address = when (val tokenType = wallet.token.type) {
            TokenType.Native -> null
            is TokenType.Jetton -> tokenType.address
            else -> return null
        }
        val balance = App.adapterManager.getBalanceAdapterForWallet(wallet)?.balanceData?.available ?: BigDecimal.ZERO
        val units = runCatching { balance.movePointRight(wallet.token.decimals).toBigIntegerExact() }
            .getOrDefault(BigInteger.ZERO)
        val asset = io.horizontalsystems.walletkit.core.managers.TonSwapAsset(
            address = address,
            name = wallet.token.coin.name,
            symbol = wallet.token.coin.code,
            decimals = wallet.token.decimals,
            balanceUnits = units,
        )
        return TonSwapAssetOption(wallet.token, wallet, balance, asset)
    }

    private fun selectedAsset(token: Token?): TonSwapAssetOption? =
        state.assets.firstOrNull { it.token == token }

    private fun assetKey(asset: io.horizontalsystems.walletkit.core.managers.TonSwapAsset): String =
        if (asset.isNative) "ton:native" else "ton:jetton:${asset.address}"

    private fun freshBalanceUnits(option: TonSwapAssetOption): BigInteger? {
        val balance = App.adapterManager.getBalanceAdapterForWallet(option.wallet)?.balanceData?.available ?: return null
        return runCatching { balance.movePointRight(option.token.decimals).toBigIntegerExact() }.getOrNull()
    }

    private fun amountUnits(value: String, decimals: Int): BigInteger? = runCatching {
        if (value.isBlank()) return null
        BigDecimal(value).setScale(decimals, RoundingMode.UNNECESSARY).unscaledValue()
    }.getOrNull()

    private fun String.hexToBase64(): String {
        if (isBlank()) return ""
        require(length % 2 == 0 && all { it.digitToIntOrNull(16) != null }) { "Invalid BoC hex" }
        val bytes = ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun bocHash(bocBase64: String): String {
        val roots = BagOfCells(Base64.decode(bocBase64, Base64.DEFAULT)).roots
        require(roots.size == 1) { "TON wallet returned an invalid signed message" }
        return roots.single().hash().toLowerHex()
    }

    private fun ByteArray.toLowerHex(): String {
        val digits = "0123456789abcdef"
        return buildString(size * 2) {
            this@toLowerHex.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(digits[value ushr 4])
                append(digits[value and 0x0f])
            }
        }
    }

    private fun clearQuoteAndReview() {
        quoteJob?.cancel()
        selectedQuote = null
        sendRequest = null
        selectedMessages = emptyList()
        state = state.copy(quote = null, quoteLoading = false, review = null, tradeStatus = null, error = null)
    }

    private fun fail(message: String) {
        state = state.copy(quoteLoading = false, working = false, error = message)
    }

    override fun onCleared() {
        quoteJob?.cancel()
        recordStatusJob?.cancel()
        if (kitManagerLinked) account?.let { plugin?.kitManager?.unlink(it) }
    }

    private companion object {
        const val DEFAULT_SLIPPAGE_PIPS = 10_000
        const val TON_DECIMALS = 9
        const val TRANSACTION_TTL_SECONDS = 300
    }
}
