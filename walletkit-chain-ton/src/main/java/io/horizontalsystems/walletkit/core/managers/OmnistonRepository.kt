package io.horizontalsystems.walletkit.core.managers

import io.horizontalsystems.tonkit.FriendlyAddress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class TonSwapAsset(
    val address: String?,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val balanceUnits: BigInteger,
) {
    val isNative: Boolean get() = address == null
}

data class OmnistonQuote(
    val quoteId: String,
    val inputAssetKey: String,
    val outputAssetKey: String,
    val inputUnits: BigInteger,
    val outputUnits: BigInteger,
    val minimumOutputUnits: BigInteger,
    val recommendedMinimumOutputUnits: BigInteger,
    val priceImpactPips: Int?,
    val protocolFeeUnits: BigInteger,
    val integratorFeeUnits: BigInteger,
    val gasBudgetUnits: BigInteger?,
    val estimatedGasUnits: BigInteger?,
    val route: String,
    val timestampSeconds: Long,
)

data class OmnistonTonMessage(
    val targetAddress: String,
    val sendAmount: Long,
    val payloadHex: String,
    val stateInitHex: String?,
)

sealed interface OmnistonQuoteEvent {
    data object Acknowledged : OmnistonQuoteEvent
    data class Updated(val quote: OmnistonQuote) : OmnistonQuoteEvent
    data object NoQuote : OmnistonQuoteEvent
}

sealed interface OmnistonSwapEvent {
    data object AwaitingTransfer : OmnistonSwapEvent
    data class Progress(
        val status: String,
        val outgoingTxHash: String?,
        val outboundTxHash: String?,
        val actualOutputUnits: BigInteger?,
        val routeCount: Int,
    ) : OmnistonSwapEvent
}

class OmnistonRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build(),
) {
    private val requestIds = AtomicLong(1)

    fun requestQuotes(
        inputAsset: TonSwapAsset,
        outputAsset: TonSwapAsset,
        inputUnits: BigInteger,
        maxSlippagePips: Int,
    ): Flow<OmnistonQuoteEvent> {
        require(inputUnits.signum() > 0) { "Input amount must be positive" }
        require(inputUnits <= inputAsset.balanceUnits) { "Input amount exceeds available balance" }
        require(maxSlippagePips in 1..MAX_SLIPPAGE_PIPS) { "Invalid slippage" }
        require(inputAsset.decimals in 0..MAX_TOKEN_DECIMALS && outputAsset.decimals in 0..MAX_TOKEN_DECIMALS) {
            "Invalid token precision"
        }
        if (inputAsset.isNative) require(inputAsset.decimals == TON_DECIMALS) { "Invalid TON precision" }
        if (outputAsset.isNative) require(outputAsset.decimals == TON_DECIMALS) { "Invalid TON precision" }
        require(inputAsset.address != outputAsset.address) { "Select different assets" }
        inputAsset.address?.let { FriendlyAddress.parse(it) }
        outputAsset.address?.let { FriendlyAddress.parse(it) }

        val params = JSONObject()
            .put("input_asset", assetJson(inputAsset))
            .put("output_asset", assetJson(outputAsset))
            .put("input_units", inputUnits.toString())
            .put(
                "settlement_params",
                JSONArray().put(
                    JSONObject().put(
                        "swap",
                        JSONObject()
                            .put("max_price_slippage_pips", maxSlippagePips)
                            .put("max_routes", MAX_TON_MESSAGES)
                            .put("allow_risky_routes", false),
                    ),
                ),
            )

        return subscriptionFlow(QUOTE_METHOD, params) { result ->
            when {
                result.has("ack") -> OmnistonQuoteEvent.Acknowledged
                result.has("no_quote") -> OmnistonQuoteEvent.NoQuote
                result.has("quote_updated") -> {
                    val quote = parseQuote(result.getJSONObject("quote_updated"))
                    if (quote.quoteId.isBlank()) OmnistonQuoteEvent.NoQuote
                    else OmnistonQuoteEvent.Updated(quote)
                }
                else -> null
            }
        }
    }

    suspend fun buildTonSwap(
        quoteId: String,
        walletAddress: String,
        useRecommendedSlippage: Boolean = false,
    ): List<OmnistonTonMessage> {
        require(quoteId.isNotBlank()) { "Quote is missing" }
        FriendlyAddress.parse(walletAddress)
        val address = JSONObject().put("ton", walletAddress)
        val params = JSONObject()
            .put("quote_id", quoteId)
            .put("transfer_src_address", address)
            .put("trader_dst_address", address)
            .put("refund_src_address", address)
            .put("gas_excess_address", address)
            .put("use_recommended_slippage", useRecommendedSlippage)

        val response = rpcCall(BUILD_SWAP_METHOD, params)
        val messages = response.optJSONArray("messages") ?: throw IOException("Omniston returned no TON messages")
        if (messages.length() !in 1..MAX_TON_MESSAGES) {
            throw IOException("Omniston returned a message count unsupported by this wallet")
        }

        return (0 until messages.length()).map { index ->
            val message = messages.getJSONObject(index)
            val addressValue = message.getString("target_address")
            FriendlyAddress.parse(addressValue)
            val amount = message.getString("send_amount").toLongOrNull()
                ?: throw IOException("Omniston returned an invalid TON message amount")
            require(amount >= 0L) { "Omniston returned a negative TON message amount" }
            val payload = message.optString("payload")
            if (payload.isNotBlank()) require(isHexBoc(payload)) { "Omniston returned an invalid payload" }
            val stateInit = message.optString("jetton_wallet_state_init").takeIf { it.isNotBlank() }
            if (stateInit != null) require(isHexBoc(stateInit)) { "Omniston returned an invalid jetton state init" }
            OmnistonTonMessage(addressValue, amount, payload, stateInit)
        }
    }

    fun trackSwap(
        quoteId: String,
        traderAddress: String,
        outgoingTxQuery: String,
    ): Flow<OmnistonSwapEvent> {
        require(quoteId.isNotBlank())
        FriendlyAddress.parse(traderAddress)
        require(outgoingTxQuery.isNotBlank())
        val params = JSONObject()
            .put("quote_id", quoteId)
            .put("trader_address", JSONObject().put("ton", traderAddress))
            .put("outgoing_tx_query", outgoingTxQuery)

        return subscriptionFlow(TRACK_SWAP_METHOD, params) { result ->
            when {
                result.has("awaiting_transfer") -> OmnistonSwapEvent.AwaitingTransfer
                result.has("progress") -> {
                    val progress = result.getJSONObject("progress")
                    val routes = progress.optJSONArray("routes")
                    OmnistonSwapEvent.Progress(
                        status = progress.opt("status")?.toString().orEmpty(),
                        outgoingTxHash = progress.optString("outgoing_tx_hash")
                            .takeIf { it.isNotBlank() && it != "null" },
                        outboundTxHash = routes?.lastStepValue("tx_hash"),
                        actualOutputUnits = routes?.actualOutputUnits(),
                        routeCount = routes?.length() ?: 0,
                    )
                }
                else -> null
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> subscriptionFlow(
        method: String,
        params: JSONObject,
        parseEvent: (JSONObject) -> T?,
    ): Flow<T> = callbackFlow {
        val id = requestIds.getAndIncrement()
        val subscriptionId = AtomicReference<Long?>(null)
        val socketRef = AtomicReference<WebSocket?>(null)
        val socket = client.newWebSocket(
            Request.Builder().url(TRADING_WS_URL).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    socketRef.set(webSocket)
                    webSocket.send(rpcRequest(id, method, params).toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val root = parseJson(text) { close(it) } ?: return
                    if (root.optLong("id", -1L) == id) {
                        val error = root.optJSONObject("error")
                        if (error != null) {
                            close(IOException("Omniston request failed: ${error.optString("message")}"))
                            webSocket.cancel()
                        } else {
                            val remoteId = root.optLong("result", -1L)
                            if (remoteId >= 0) subscriptionId.set(remoteId)
                        }
                        return
                    }

                    when (root.optString("method")) {
                        method -> {
                            val notification = root.optJSONObject("params") ?: return
                            val remoteId = notification.optLong("subscription", -1L)
                            val activeId = subscriptionId.get()
                            if (activeId != null && remoteId != activeId) return
                            notification.optJSONObject("error")?.let { error ->
                                close(IOException("Omniston stream failed: ${error.optString("message")}"))
                                webSocket.cancel()
                                return
                            }
                            val result = notification.optJSONObject("result") ?: return
                            runCatching { parseEvent(result) }
                                .onSuccess { event -> if (event != null) trySend(event) }
                                .onFailure { close(IOException("Invalid Omniston event", it)) }
                        }
                        "$method.closed" -> close()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    close(IOException("Omniston WebSocket failed", t))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    close()
                }
            },
        )
        socketRef.set(socket)
        awaitClose {
            val activeId = subscriptionId.get()
            socketRef.getAndSet(null)?.let { webSocket ->
                if (activeId != null) {
                    webSocket.send(
                        JSONObject()
                            .put("jsonrpc", "2.0")
                            .put("id", requestIds.getAndIncrement())
                            .put("method", "$method.unsubscribe")
                            .put("params", JSONArray().put(activeId))
                            .toString(),
                    )
                    webSocket.close(1000, "Unsubscribed")
                } else {
                    webSocket.cancel()
                }
            }
        }
    }

    private suspend fun rpcCall(method: String, params: JSONObject): JSONObject {
        val id = requestIds.getAndIncrement()
        return withTimeout(RPC_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
            val socketRef = AtomicReference<WebSocket?>(null)
            val socket = client.newWebSocket(
                Request.Builder().url(TRADING_WS_URL).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        socketRef.set(webSocket)
                        webSocket.send(rpcRequest(id, method, params).toString())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val root = try {
                            JSONObject(text)
                        } catch (e: Exception) {
                            resumeFailure(continuation, IOException("Invalid Omniston response", e), webSocket)
                            return
                        }
                        if (root.optLong("id", -1L) != id) return
                        val error = root.optJSONObject("error")
                        if (error != null) {
                            resumeFailure(
                                continuation,
                                IOException("Omniston request failed: ${error.optString("message")}"),
                                webSocket,
                            )
                            return
                        }
                        val result = root.optJSONObject("result")
                        if (result == null) {
                            resumeFailure(continuation, IOException("Omniston returned an empty response"), webSocket)
                        } else if (continuation.isActive) {
                            continuation.resumeWith(Result.success(result))
                            webSocket.close(1000, null)
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        resumeFailure(continuation, IOException("Omniston request failed", t), webSocket)
                    }
                },
            )
            socketRef.set(socket)
            continuation.invokeOnCancellation { socketRef.getAndSet(null)?.cancel() }
            }
        }
    }

    private fun resumeFailure(
        continuation: kotlinx.coroutines.CancellableContinuation<JSONObject>,
        error: Throwable,
        socket: WebSocket,
    ) {
        if (continuation.isActive) continuation.resumeWith(Result.failure(error))
        socket.cancel()
    }

    private fun parseQuote(json: JSONObject): OmnistonQuote {
        val settlement = json.getJSONObject("swap")
        val routes = settlement.optJSONArray("routes") ?: JSONArray()
        val routeNames = mutableListOf<String>()
        for (routeIndex in 0 until routes.length()) {
            val route = routes.getJSONObject(routeIndex)
            val steps = route.optJSONArray("steps") ?: JSONArray()
            for (stepIndex in 0 until steps.length()) {
                val chunks = steps.getJSONObject(stepIndex).optJSONArray("chunks") ?: JSONArray()
                for (chunkIndex in 0 until chunks.length()) {
                    val protocol = chunks.getJSONObject(chunkIndex).optString("protocol")
                    if (protocol.isNotBlank()) routeNames += protocol
                }
            }
        }

        return OmnistonQuote(
            quoteId = json.optString("quote_id"),
            inputAssetKey = assetKey(json.getJSONObject("input_asset")),
            outputAssetKey = assetKey(json.getJSONObject("output_asset")),
            inputUnits = json.optString("input_units").toBigInteger(),
            outputUnits = json.optString("output_units").toBigInteger(),
            minimumOutputUnits = settlement.getString("min_output_amount").toBigInteger(),
            recommendedMinimumOutputUnits = settlement.getString("recommended_min_output_amount").toBigInteger(),
            priceImpactPips = if (settlement.has("price_impact_pips")) settlement.getInt("price_impact_pips") else null,
            protocolFeeUnits = json.optString("protocol_fee_units", "0").toBigIntegerOrZero(),
            integratorFeeUnits = json.optString("integrator_fee_units", "0").toBigIntegerOrZero(),
            gasBudgetUnits = json.optString("gas_budget").toBigIntegerOrNull(),
            estimatedGasUnits = json.optString("estimated_gas_consumption").toBigIntegerOrNull(),
            route = routeNames.distinct().joinToString(" → "),
            timestampSeconds = json.optLong("quote_timestamp"),
        )
    }

    private fun assetJson(asset: TonSwapAsset): JSONObject {
        val tonId = if (asset.isNative) {
            JSONObject().put("native", JSONObject())
        } else {
            JSONObject().put("jetton", requireNotNull(asset.address))
        }
        return JSONObject().put("ton", tonId)
    }

    private fun assetKey(asset: JSONObject): String {
        val ton = asset.optJSONObject("ton") ?: throw IOException("Omniston returned a non-TON asset")
        return when {
            ton.has("native") -> "ton:native"
            ton.has("jetton") -> "ton:jetton:${ton.getString("jetton")}"
            else -> throw IOException("Omniston returned an unsupported TON asset")
        }
    }

    private fun JSONArray.lastStepValue(field: String): String? {
        var value: String? = null
        for (routeIndex in 0 until length()) {
            val steps = optJSONObject(routeIndex)?.optJSONArray("steps") ?: continue
            if (steps.length() == 0) continue
            val lastStep = steps.optJSONObject(steps.length() - 1) ?: continue
            val chunks = lastStep.optJSONArray("chunks") ?: continue
            for (chunkIndex in 0 until chunks.length()) {
                chunks.optJSONObject(chunkIndex)?.optString(field)
                    ?.takeIf { it.isNotBlank() && it != "null" }
                    ?.let { value = it }
            }
        }
        return value
    }

    private fun JSONArray.actualOutputUnits(): BigInteger? {
        var total = BigInteger.ZERO
        var found = false
        for (routeIndex in 0 until length()) {
            val steps = optJSONObject(routeIndex)?.optJSONArray("steps") ?: continue
            if (steps.length() == 0) continue
            val lastStep = steps.optJSONObject(steps.length() - 1) ?: continue
            val chunks = lastStep.optJSONArray("chunks") ?: continue
            for (chunkIndex in 0 until chunks.length()) {
                val units = chunks.optJSONObject(chunkIndex)?.optString("actual_output_units")
                    ?.toBigIntegerOrNull() ?: continue
                if (units.signum() > 0) {
                    total += units
                    found = true
                }
            }
        }
        return total.takeIf { found }
    }

    private fun rpcRequest(id: Long, method: String, params: JSONObject) = JSONObject()
        .put("jsonrpc", "2.0")
        .put("id", id)
        .put("method", method)
        .put("params", params)

    private fun parseJson(text: String, onError: (Throwable) -> Unit): JSONObject? = try {
        JSONObject(text)
    } catch (e: Exception) {
        onError(IOException("Invalid Omniston event", e))
        null
    }

    private fun isHexBoc(value: String): Boolean =
        value.isNotBlank() && value.length % 2 == 0 && value.all { it.digitToIntOrNull(16) != null }

    private fun String.toBigIntegerOrZero(): BigInteger = toBigIntegerOrNull() ?: BigInteger.ZERO

    private companion object {
        const val TRADING_WS_URL = "wss://omni-ws.ston.fi"
        const val QUOTE_METHOD = "stonfi.omni.v1beta8.QuoteRpc.Quote"
        const val BUILD_SWAP_METHOD = "stonfi.omni.v1beta8.TonRpc.BuildSwap"
        const val TRACK_SWAP_METHOD = "stonfi.omni.v1beta8.SwapRpc.Track"
        const val MAX_SLIPPAGE_PIPS = 1_000_000
        const val MAX_TON_MESSAGES = 4
        const val MAX_TOKEN_DECIMALS = 255
        const val TON_DECIMALS = 9
        const val RPC_TIMEOUT_MS = 20_000L
    }
}

private fun String.toBigIntegerOrNull(): BigInteger? = runCatching { BigInteger(this) }.getOrNull()
