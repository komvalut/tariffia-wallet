plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose) // MainActivity hosts the Compose nav entry

    // Optional, provides the @Serialize annotation for autogeneration of Serializers.
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// buildConfigField expects a Java expression, so plain strings need embedded quotes
fun com.android.build.api.dsl.VariantDimension.buildConfigFieldString(name: String, value: String) {
    buildConfigField("String", name, "\"$value\"")
}

fun secret(name: String): String = System.getenv(name).orEmpty()

android {
    namespace = "io.horizontalsystems.bankwallet"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.tariffia.wallet"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = 185
        versionName = "0.54.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        resourceConfigurations += listOf("de", "es", "en", "fa", "fr", "ko", "pt", "pt-rBR", "ru", "tr", "zh")

        vectorDrawables.useSupportLibrary = true

        buildConfigFieldString("COMPANY_WEB_PAGE_LINK", "https://horizontalsystems.io")
        buildConfigFieldString("APP_WEB_PAGE_LINK", "https://unstoppable.money")
        buildConfigFieldString("ANALYTICS_LINK", "https://unstoppable.money/analytics")
        buildConfigFieldString("APP_GITHUB_LINK", "https://github.com/horizontalsystems/unstoppable-wallet-android")
        buildConfigFieldString("APP_TWITTER_LINK", "https://twitter.com/UnstoppableByHS")
        buildConfigFieldString("APP_TELEGRAM_LINK", "https://t.me/unstoppable_announcements")
        buildConfigFieldString("REPORT_EMAIL", "support.unstoppable@protonmail.com")
        buildConfigFieldString("RELEASE_NOTES_URL", "https://api.github.com/repos/horizontalsystems/unstoppable-wallet-android/releases/tags/")
        buildConfigFieldString("WALLET_CONNECT_APP_META_DATA_NAME", "Unstoppable")
        buildConfigFieldString("WALLET_CONNECT_APP_META_DATA_URL", "unstoppable.money")
        buildConfigFieldString("WALLET_CONNECT_APP_META_DATA_ICON", "https://raw.githubusercontent.com/horizontalsystems/HS-Design/master/PressKit/UW-AppIcon-on-light.png")
        buildConfigFieldString("THORCHAIN_API_KEY", secret("THORCHAIN_API_KEY"))

        buildConfigField("boolean", "FDROID_BUILD", "false")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("test") {
            storeFile = file("./test.keystore")
            storePassword = "testKeystore123"
            keyAlias = "testKeystore"
            keyPassword = "testKeystore123"
        }
    }

    flavorDimensions += "distribution"

    val uswapApiKeyAndroid = secret("USWAP_API_KEY_ANDROID")
    val uswapApiKeyFdroid = secret("USWAP_API_KEY_FDROID")

    productFlavors {
        create("base") {
            dimension = "distribution"
            buildConfigFieldString("USWAP_API_KEY", uswapApiKeyAndroid)
        }

        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "FDROID_BUILD", "true")
            buildConfigFieldString("USWAP_API_KEY", uswapApiKeyFdroid)
        }

        create("fdroidCi") {
            dimension = "distribution"
            applicationIdSuffix = ".fdroidci"
            buildConfigField("boolean", "FDROID_BUILD", "true")
            signingConfig = signingConfigs.getByName("test")
            buildConfigFieldString("USWAP_API_KEY", uswapApiKeyFdroid)
        }

        create("ci") {
            dimension = "distribution"
            applicationIdSuffix = ".appcenter"
            versionCode = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: defaultConfig.versionCode
            signingConfig = signingConfigs.getByName("test")
            buildConfigFieldString("APP_LINKS_HOST", "dev.unstoppable.money")
            manifestPlaceholders["appLinksHost"] = "dev.unstoppable.money"
            buildConfigFieldString("USWAP_API_KEY", uswapApiKeyAndroid)
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("test")
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = ".dev"
            buildConfigFieldString("APP_LINKS_HOST", "dev.unstoppable.money")
            manifestPlaceholders["appLinksHost"] = "dev.unstoppable.money"
            buildConfigFieldString("TWITTER_BEARER_TOKEN", secret("TWITTER_BEARER_TOKEN"))
            buildConfigFieldString("ETHERSCAN_KEY", secret("ETHERSCAN_KEY"))
            buildConfigFieldString("BSCSCAN_KEY", secret("BSCSCAN_KEY"))
            buildConfigFieldString("BLOCKSCOUT_KEY", secret("BLOCKSCOUT_KEY"))
            buildConfigFieldString("GUIDES_URL", "https://raw.githubusercontent.com/horizontalsystems/blockchain-crypto-guides/v1.2/index.json")
            buildConfigFieldString("FAQ_URL", "https://raw.githubusercontent.com/horizontalsystems/unstoppable.money/refs/heads/main/faq.json")
            buildConfigFieldString("COINS_JSON_URL", "https://raw.githubusercontent.com/horizontalsystems/cryptocurrencies/master/coins.json")
            buildConfigFieldString("PROVIDER_COINS_JSON_URL", "https://raw.githubusercontent.com/horizontalsystems/cryptocurrencies/master/provider.coins.json")
            buildConfigFieldString("MARKET_API_BASE_URL", "https://api-dev.blocksdecoded.com")
            buildConfigFieldString("MARKET_API_KEY", secret("MARKET_API_KEY"))
            buildConfigFieldString("WALLET_CONNECT_V2_KEY", secret("WALLET_CONNECT_V2_KEY"))
            buildConfigFieldString("ALCHEMY_API_KEY", secret("ALCHEMY_API_KEY"))
            buildConfigFieldString("SOLANA_JUPITER_API_KEY", secret("SOLANA_JUPITER_API_KEY"))
            buildConfigFieldString("TONAPI_KEY", secret("TONAPI_KEY"))
            buildConfigFieldString("TRONGRID_API_KEYS", secret("TRONGRID_API_KEYS"))
            buildConfigFieldString("UDN_API_KEY", secret("UDN_API_KEY"))
            buildConfigFieldString("BLOCKS_DECODED_ETHEREUM_RPC", "https://ethereum-rpc.unstoppable.money")
            buildConfigFieldString("CHAINALYSIS_BASE_URL", "https://public.chainalysis.com/api/v1/")
            buildConfigFieldString("CHAINALYSIS_API_KEY", secret("CHAINALYSIS_API_KEY"))
            buildConfigFieldString("HASH_DIT_BASE_URL", "https://service.hashdit.io/v2/hashdit/")
            buildConfigFieldString("HASH_DIT_API_KEY", secret("HASH_DIT_API_KEY"))
            buildConfigFieldString("USWAP_API_BASE_URL", "https://swap-dev.unstoppable.money/api/v3/")
            buildConfigFieldString("FASTNEAR_API_KEY", secret("FASTNEAR_API_KEY"))
        }

        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigFieldString("APP_LINKS_HOST", "unstoppable.money")
            manifestPlaceholders["appLinksHost"] = "unstoppable.money"
            buildConfigFieldString("TWITTER_BEARER_TOKEN", secret("TWITTER_BEARER_TOKEN"))
            buildConfigFieldString("ETHERSCAN_KEY", secret("ETHERSCAN_KEY"))
            buildConfigFieldString("BSCSCAN_KEY", secret("BSCSCAN_KEY"))
            buildConfigFieldString("BLOCKSCOUT_KEY", secret("BLOCKSCOUT_KEY"))
            buildConfigFieldString("GUIDES_URL", "https://raw.githubusercontent.com/horizontalsystems/blockchain-crypto-guides/v1.2/index.json")
            buildConfigFieldString("FAQ_URL", "https://raw.githubusercontent.com/horizontalsystems/unstoppable.money/refs/heads/main/faq.json")
            buildConfigFieldString("COINS_JSON_URL", "https://raw.githubusercontent.com/horizontalsystems/cryptocurrencies/v0.21/coins.json")
            buildConfigFieldString("PROVIDER_COINS_JSON_URL", "https://raw.githubusercontent.com/horizontalsystems/cryptocurrencies/v0.21/provider.coins.json")
            buildConfigFieldString("MARKET_API_BASE_URL", "https://api.blocksdecoded.com")
            buildConfigFieldString("MARKET_API_KEY", secret("MARKET_API_KEY"))
            buildConfigFieldString("WALLET_CONNECT_V2_KEY", secret("WALLET_CONNECT_V2_KEY"))
            buildConfigFieldString("ALCHEMY_API_KEY", secret("ALCHEMY_API_KEY"))
            buildConfigFieldString("SOLANA_JUPITER_API_KEY", secret("SOLANA_JUPITER_API_KEY"))
            buildConfigFieldString("TONAPI_KEY", secret("TONAPI_KEY"))
            buildConfigFieldString("TRONGRID_API_KEYS", secret("TRONGRID_API_KEYS"))
            buildConfigFieldString("UDN_API_KEY", secret("UDN_API_KEY"))
            buildConfigFieldString("BLOCKS_DECODED_ETHEREUM_RPC", "https://ethereum-rpc.unstoppable.money")
            buildConfigFieldString("CHAINALYSIS_BASE_URL", "https://public.chainalysis.com/api/v1/")
            buildConfigFieldString("CHAINALYSIS_API_KEY", secret("CHAINALYSIS_API_KEY"))
            buildConfigFieldString("HASH_DIT_BASE_URL", "https://service.hashdit.io/v2/hashdit/")
            buildConfigFieldString("HASH_DIT_API_KEY", secret("HASH_DIT_API_KEY"))
            buildConfigFieldString("USWAP_API_BASE_URL", "https://swap-api.unstoppable.money/v3/")
            buildConfigFieldString("FASTNEAR_API_KEY", secret("FASTNEAR_API_KEY"))
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            pickFirsts += listOf(
                    "META-INF/atomicfu.kotlin_module",
                    "META-INF/FastDoubleParser-LICENSE",
                    "META-INF/FastDoubleParser-NOTICE",
                    "META-INF/io.netty.versions.properties"
            )
            excludes += listOf(
                    "META-INF/INDEX.LIST",
                    "META-INF/DEPENDENCIES",
                    "META-INF/LICENSE.md"
            )
        }
        jniLibs {
            useLegacyPackaging = true
        }
        dex {
            useLegacyPackaging = true
        }
    }

    lint {
        disable += "LogNotTimber"
        disable += "RemoveWorkManagerInitializer"
    }

    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.bouncycastle:bcprov-jdk15to18:1.68")).using(module("org.bouncycastle:bcprov-jdk15on:1.70"))
            substitute(module("com.google.protobuf:protobuf-java:3.6.1")).using(module("com.google.protobuf:protobuf-javalite:3.21.1"))
            substitute(module("net.jcip:jcip-annotations:1.0")).using(module("com.github.stephenc.jcip:jcip-annotations:1.0-1"))

            substitute(module("com.tinder.scarlet:scarlet:0.1.12")).using(module("com.walletconnect.Scarlet:scarlet:1.0.2"))
            substitute(module("com.tinder.scarlet:websocket-okhttp:0.1.12")).using(module("com.walletconnect.Scarlet:websocket-okhttp:1.0.2"))
            substitute(module("com.tinder.scarlet:stream-adapter-rxjava2:0.1.12")).using(module("com.walletconnect.Scarlet:stream-adapter-rxjava2:1.0.2"))
            substitute(module("com.tinder.scarlet:message-adapter-gson:0.1.12")).using(module("com.walletconnect.Scarlet:message-adapter-gson:1.0.2"))
            substitute(module("com.tinder.scarlet:lifecycle-android:0.1.12")).using(module("com.walletconnect.Scarlet:lifecycle-android:1.0.2"))
            substitute(module("com.github.WalletConnect.Scarlet:scarlet:1.0.0")).using(module("com.walletconnect.Scarlet:scarlet:1.0.2"))
            substitute(module("com.github.WalletConnect.Scarlet:websocket-okhttp:1.0.0")).using(module("com.walletconnect.Scarlet:websocket-okhttp:1.0.2"))
            substitute(module("com.github.WalletConnect.Scarlet:stream-adapter-rxjava2:1.0.0")).using(module("com.walletconnect.Scarlet:stream-adapter-rxjava2:1.0.2"))
            substitute(module("com.github.WalletConnect.Scarlet:message-adapter-gson:1.0.0")).using(module("com.walletconnect.Scarlet:message-adapter-gson:1.0.2"))
            substitute(module("com.github.WalletConnect.Scarlet:lifecycle-android:1.0.0")).using(module("com.walletconnect.Scarlet:lifecycle-android:1.0.2"))
        }

        resolutionStrategy.eachDependency {
            if (requested.group == "com.squareup.okhttp3") {
                useVersion("4.12.0")
            }
        }
    }
}

dependencies {
    // Everything else now lives in :walletkit, which re-exports its dependencies (api),
    // so the thin :app shell only needs :walletkit plus the flavor/test bits below.
    implementation(project(":walletkit"))
    implementation(project(":walletkit-chain-zano"))
    implementation(project(":walletkit-chain-monero"))
    implementation(project(":walletkit-chain-zcash"))
    implementation(project(":walletkit-chain-solana"))
    implementation(project(":walletkit-chain-stellar"))
    implementation(project(":walletkit-chain-xrp"))
    implementation(project(":walletkit-chain-near"))
    implementation(project(":walletkit-chain-ton"))
    implementation(project(":walletkit-chain-tron"))
    implementation(project(":walletkit-chain-thorchain"))
    implementation(project(":walletkit-chain-bitcoin"))
    implementation(project(":walletkit-chain-evm"))
    implementation(libs.androidx.splashscreen) // MainActivity installs the splash screen
    debugImplementation(libs.leakcanary)

    // Desugar
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Exclude old version from wherever it's coming
    configurations.configureEach {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
        exclude(group = "org.bouncycastle", module = "bcutil-jdk18on")
    }

    // Spongycastle's POM declares junit as a compile dependency. LeakCanary disables
    // heap dumps when it finds org.junit.Test on the classpath, so keep junit out of the APK.
    configurations.matching { it.name.endsWith("RuntimeClasspath") && !it.name.contains("Test") }.configureEach {
        exclude(group = "junit", module = "junit")
    }

    // UI Tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Supplies the ComponentActivity that Compose UI tests are hosted in
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.espresso.core)

    // Unit Tests
    testImplementation(libs.junit)
    testImplementation(libs.classgraph)
    testImplementation(libs.arch.core.testing)
    testImplementation(libs.mockito)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.powermock.api.mockito2)
    testImplementation(libs.powermock.module.junit4)
    testImplementation(libs.spek.dsl.jvm)
    testRuntimeOnly(libs.spek.runner.junit5)
    testRuntimeOnly(libs.kotlin.reflect)
}

// Flavor-specific dependencies must be added after evaluation
afterEvaluate {
    dependencies {
        "baseDebugImplementation"(project(":subscriptions-dev"))
        findProject(":subscriptions-google-play")?.let {
            "baseReleaseImplementation"(it)
        }

        "fdroidImplementation"(project(":subscriptions-fdroid"))
        "fdroidCiImplementation"(project(":subscriptions-fdroid"))
        "ciImplementation"(project(":subscriptions-dev"))

        findProject(":dapp-wallet-connect")?.let {
            "baseDebugImplementation"(it)
            "baseReleaseImplementation"(it)
            "ciImplementation"(it)
        }

        "baseDebugImplementation"(libs.androidx.credentials.play.services.auth)
        "baseReleaseImplementation"(libs.androidx.credentials.play.services.auth)
        "ciImplementation"(libs.androidx.credentials.play.services.auth)
    }
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, TimeUnit.SECONDS)
        // PowerMock forces junit:4.12 which conflicts with androidTest deps requiring 4.13.2
        force("junit:junit:4.13.2")

        // Force Ktor version for TonKit
        force("io.ktor:ktor-utils:2.3.7")
        force("io.ktor:ktor-io:2.3.7")
        force("io.ktor:ktor-client-core:2.3.7")
    }
}

// Forward -PupdateParityFixture=true to the test JVM so ChainBehaviorParityTest can
// regenerate its golden fixture (see walletkit/docs/Walletkit-Modularization-Plan.md).
tasks.withType<Test>().configureEach {
    systemProperty(
        "updateParityFixture",
        providers.gradleProperty("updateParityFixture").getOrElse("false")
    )
}
