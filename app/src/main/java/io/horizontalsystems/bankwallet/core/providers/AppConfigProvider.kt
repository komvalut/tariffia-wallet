package io.horizontalsystems.bankwallet.core.providers

import io.horizontalsystems.bankwallet.BuildConfig
import io.horizontalsystems.bankwallet.R
import io.horizontalsystems.walletkit.core.ILocalStorage
import io.horizontalsystems.walletkit.core.order
import io.horizontalsystems.walletkit.core.providers.IAppConfigProvider
import io.horizontalsystems.walletkit.entities.Currency
import io.horizontalsystems.marketkit.models.BlockchainType
import java.math.BigDecimal

class AppConfigProvider(localStorage: ILocalStorage) : IAppConfigProvider {

    override val appId by lazy { localStorage.appId }
    override val appVersion by lazy { BuildConfig.VERSION_NAME }
    override val appBuild by lazy { BuildConfig.VERSION_CODE }
    override val companyWebPageLink = BuildConfig.COMPANY_WEB_PAGE_LINK
    override val appWebPageLink = BuildConfig.APP_WEB_PAGE_LINK
    override val analyticsLink = BuildConfig.ANALYTICS_LINK
    override val appGithubLink = BuildConfig.APP_GITHUB_LINK
    override val reportEmail = BuildConfig.REPORT_EMAIL
    override val releaseNotesUrl = BuildConfig.RELEASE_NOTES_URL
    override val mempoolSpaceUrl: String = "https://mempool.space"
    override val blockCypherUrl: String = "https://api.blockcypher.com"
    override val walletConnectUrl = "relay.walletconnect.com"
    override val walletConnectProjectId = BuildConfig.WALLET_CONNECT_PROJECT_ID
    override val walletConnectAppMetaDataName = BuildConfig.WALLET_CONNECT_APP_META_DATA_NAME
    override val walletConnectAppMetaDataUrl = BuildConfig.WALLET_CONNECT_APP_META_DATA_URL
    override val walletConnectAppMetaDataIcon = BuildConfig.WALLET_CONNECT_APP_META_DATA_ICON

    override val blocksDecodedEthereumRpc = BuildConfig.BLOCKS_DECODED_ETHEREUM_RPC
    override val twitterBearerToken = BuildConfig.TWITTER_BEARER_TOKEN
    override val etherscanApiKey = BuildConfig.ETHERSCAN_KEY.split(",")
    override val bscscanApiKey = BuildConfig.BSCSCAN_KEY.split(",")
    override val blockscoutApiKey = BuildConfig.BLOCKSCOUT_KEY.split(",")
    override val alchemyApiKey = BuildConfig.ALCHEMY_API_KEY.split(",")
    override val guidesUrl = BuildConfig.GUIDES_URL
    override val faqUrl = BuildConfig.FAQ_URL
    override val coinsJsonUrl = BuildConfig.COINS_JSON_URL
    override val providerCoinsJsonUrl = BuildConfig.PROVIDER_COINS_JSON_URL
    override val marketApiBaseUrl = BuildConfig.MARKET_API_BASE_URL
    override val marketApiKey = BuildConfig.MARKET_API_KEY
    override val trongridApiKeys: List<String> = BuildConfig.TRONGRID_API_KEYS.split(",")
    override val udnApiKey = BuildConfig.UDN_API_KEY
    override val thorchainApiKey = BuildConfig.THORCHAIN_API_KEY
    override val appLinksHost = BuildConfig.APP_LINKS_HOST
    override val passkeyRpId = BuildConfig.PASSKEY_RP_ID.ifBlank { appLinksHost }
    override val passkeyRpName = BuildConfig.PASSKEY_RP_NAME

    override val fiatDecimal: Int = 2
    override val feeRateAdjustForCurrencies: List<String> = listOf("USD", "EUR")

    override val currencies: List<Currency> = listOf(
        Currency("AUD", "A$", 2, R.drawable.icon_32_flag_australia),
        Currency("ARS", "$", 2, R.drawable.icon_32_flag_argentine),
        Currency("BRL", "R$", 2, R.drawable.icon_32_flag_brazil),
        Currency("CAD", "C$", 2, R.drawable.icon_32_flag_canada),
        Currency("CHF", "₣", 2, R.drawable.icon_32_flag_switzerland),
        Currency("CNY", "¥", 2, R.drawable.icon_32_flag_china),
        Currency("EUR", "€", 2, R.drawable.icon_32_flag_europe),
        Currency("GBP", "£", 2, R.drawable.icon_32_flag_england),
        Currency("HKD", "HK$", 2, R.drawable.icon_32_flag_hongkong),
        Currency("HUF", "Ft", 2, R.drawable.icon_32_flag_hungary),
        Currency("ILS", "₪", 2, R.drawable.icon_32_flag_israel),
        Currency("INR", "₹", 2, R.drawable.icon_32_flag_india),
        Currency("JPY", "¥", 2, R.drawable.icon_32_flag_japan),
        Currency("NOK", "kr", 2, R.drawable.icon_32_flag_norway),
        Currency("PHP", "₱", 2, R.drawable.icon_32_flag_philippine),
        Currency("RUB", "₽", 2, R.drawable.icon_32_flag_russia),
        Currency("SGD", "S$", 2, R.drawable.icon_32_flag_singapore),
        Currency("USD", "$", 2, R.drawable.icon_32_flag_usa),
        Currency("ZAR", "R", 2, R.drawable.icon_32_flag_south_africa),
    )

    // coinCode -> risk threshold (used for dust detection scoring)
    // spam = risk/10 (auto-spam, except spamCoinsWithoutMicroDust), risk = config value (+3 points), danger = risk*5 (+2 points)
    override val spamCoinValueLimits: Map<String, BigDecimal> = mapOf(
        "XLM" to BigDecimal("0.1"),
        // XRPL dust spam is sent in single drops; anything under 0.0001 XRP (limit / 10) is auto-spam
        "XRP" to BigDecimal("0.001"),
        "USDT" to BigDecimal("1"),
        "USDC" to BigDecimal("1"),
        "USDD" to BigDecimal("1"),
        "DAI" to BigDecimal("1"),
        "BUSD" to BigDecimal("1"),
        "EURS" to BigDecimal("1"),
        "BSC-USD" to BigDecimal("1"),
        "TRX" to BigDecimal("1"),
        "ETH" to BigDecimal("0.0005"),
        "POL" to BigDecimal("1"),
        "BNB" to BigDecimal("0.0002"),
        "SOL" to BigDecimal("0.0001"),
        "NEAR" to BigDecimal("0.01"),
    )

    // Cent-sized stablecoin transfers are common test sends, so their micro dust is not
    // auto-spam: it scores as ordinary dust and needs address or time correlation.
    override val spamCoinsWithoutMicroDust: Set<String> = setOf(
        "USDT", "USDC", "USDD", "DAI", "BUSD", "EURS", "BSC-USD",
    )

    override val chainalysisBaseUrl = BuildConfig.CHAINALYSIS_BASE_URL

    override val chainalysisApiKey = BuildConfig.CHAINALYSIS_API_KEY

    override val hashDitBaseUrl = BuildConfig.HASH_DIT_BASE_URL

    override val hashDitApiKey = BuildConfig.HASH_DIT_API_KEY

    override val uswapApiBaseUrl = BuildConfig.USWAP_API_BASE_URL

    override val uswapApiKey = BuildConfig.USWAP_API_KEY

    override val fastNearApiKey = BuildConfig.FASTNEAR_API_KEY.ifBlank { null }

    override val tonApiKey = BuildConfig.TONAPI_KEY



    override val fdroidBuild: Boolean = BuildConfig.FDROID_BUILD
}
