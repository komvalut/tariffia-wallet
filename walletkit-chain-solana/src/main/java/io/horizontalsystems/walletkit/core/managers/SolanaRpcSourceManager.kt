package io.horizontalsystems.walletkit.core.managers

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import io.horizontalsystems.walletkit.core.storage.BlockchainSettingsStorage
import io.horizontalsystems.marketkit.models.Blockchain
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.solanakit.models.RpcSource

class SolanaRpcSourceManager(
        private val blockchainSettingsStorage: BlockchainSettingsStorage,
        private val marketKitWrapper: MarketKitWrapper,
        alchemyApiKey: String,
) {

    private val blockchainType = BlockchainType.Solana
    private val _rpcSourceUpdateFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val rpcSourceUpdateFlow: SharedFlow<Unit>
        get() = _rpcSourceUpdateFlow.asSharedFlow()

    // Alchemy requires at least one API key. Builds without ALCHEMY_API_KEY (e.g. CI) must not
    // call RpcSource.Alchemy("") because its endpoint selection does Random.nextInt(0) and crashes.
    val allRpcSources = if (alchemyApiKey.isBlank()) emptyList() else listOf(RpcSource.Alchemy(alchemyApiKey))

    val rpcSource: RpcSource
        get() {
            if (allRpcSources.isEmpty()) {
                throw IllegalStateException("No Solana RPC source configured: ALCHEMY_API_KEY is missing")
            }
            val rpcSourceName = blockchainSettingsStorage.evmSyncSourceUrl(blockchainType)
            val rpcSource = allRpcSources.firstOrNull { it.name == rpcSourceName }

            return rpcSource ?: allRpcSources[0]
        }

    val blockchain: Blockchain?
        get() = marketKitWrapper.blockchain(blockchainType.uid)

    fun save(rpcSource: RpcSource) {
        blockchainSettingsStorage.save(rpcSource.name, blockchainType)
        _rpcSourceUpdateFlow.tryEmit(Unit)
    }

}
