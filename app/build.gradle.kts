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

val releaseStoreFile = System.getenv("TARIFFIA_KEYSTORE_FILE").orEmpty()
val releaseStorePassword = System.getenv("TARIFFIA_KEYSTORE_PASSWORD").orEmpty()
val releaseKeyAlias = System.getenv("TARIFFIA_KEY_ALIAS").orEmpty()
val releaseKeyPassword = System.getenv("TARIFFIA_KEY_PASSWORD").orEmpty()
val releaseSigningComplete = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it.isNotBlank() }
val requireReleaseSigning = System.getenv("TARIFFIA_REQUIRE_SIGNING").toBoolean()

android {
    namespace = "io.horizontalsystems.bankwallet"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.tariffia.wallet"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = 185
        versionName = "1.0.0-rc1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        resourceConfigurations += listOf("de", "es", "en", "fa", "fr", "ko", "pt", "pt-rBR", "ru", "tr", "zh")

        vectorDrawables.useSupportLibrary = true

        buildConfigFieldString("COMPANY_WEB_PAGE_LINK", "https://tariffia.app")
        buildConfigFieldString("APP_WEB_PAGE_LINK", "https://tariffia.app")
        buildConfigFieldString("ANALYTICS_LINK", "https://tariffia.app/analytics")
        buildConfigFieldString("APP_GITHUB_LINK", "https://github.com/komvalut/tariffia-wallet")
        buildConfigFieldString("REPORT_EMAIL", "support@tariffia.app")
        buildConfigFieldString("RELEASE_NOTES_URL", "https://api.github.com/repos/komvalut/tariffia-wallet/releases/tags/")
        buildConfigFieldString("WALLET_CONNECT_PROJECT_ID", secret("WALLET_CONNECT_PROJECT_ID"))
        buildConfigFieldString("WALLET_CONNECT_APP_META_DATA_NAME", "Tariffia Wallet")
        buildConfigFieldString("WALLET_CONNECT_APP_META_DATA_URL", "https://tariffia.app")
        buildConfigFieldString("WALLET_CONNECT_APP_META_DATA_ICON", "https://tariffia.app/icon.png")
        buildConfigFieldString("PASSKEY_RP_ID", secret("PASSKEY_RP_ID"))
        buildConfigFieldString("PASSKEY_RP_NAME", "Tariffia Wallet")
        buildConfigFieldString("THORCHAIN_API_KEY", secret("THORCHAIN_API_KEY"))

        buildConfigField("boolean", "FDROID_BUILD", "false")
        buildConfigField("boolean", "SWAP_ENABLED", "false")
        buildConfigFieldString("USWAP_API_KEY", "")

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

    val releaseSigningConfig = if (releaseSigningComplete) {
        signingConfigs.create("release") {
            storeFile = file(releaseStoreFile)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    } else {
        null
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("base") {
            dimension = "distribution"
        }

        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "FDROID_BUILD", "true")
        }

        create("fdroidCi") {
            dimension = "distribution"
            applicationIdSuffix = ".fdroidci"
            buildConfigField("boolean", "FDROID_BUILD", "true")
            signingConfig = signingConfigs.getByName("test")
        }

        create("ci") {
            dimension = "distribution"
            applicationIdSuffix = ".appcenter"
            versionCode = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: defaultConfig.versionCode
            signingConfig = signingConfigs.getByName("test")
            buildConfigFieldString("APP_LINKS_HOST", "dev.tariffia.app")
            manifestPlaceholders["appLinksHost"] = "dev.tariffia.app"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("test")
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = ".dev"
            buildConfigFieldString("APP_LINKS_HOST", "dev.tariffia.app")
            manifestPlaceholders["appLinksHost"] = "dev.tariffia.app"
            buildConfigFieldString("TWITTER_BEARER_TOKEN", secret("TWITTER_BEARER_TOKEN"))
            buildConfigFieldString("ETHERSCAN_KEY", secret("ETHERSCAN_KEY"))
            buildConfigFieldString("BSCSCAN_KEY", secret("BSCSCAN_KEY"))
            buildConfigFieldString("BLOCKSCOUT_KEY", secret("BLOCKSCOUT_KEY"))
            buildConfigFieldString("GUIDES_URL", "https://dev.tariffia.app/guides/index.json")
            buildConfigFieldString("FAQ_URL", "https://dev.tariffia.app/faq.json")
            buildConfigFieldString("COINS_JSON_URL", "https://dev.tariffia.app/data/coins.json")
            buildConfigFieldString("PROVIDER_COINS_JSON_URL", "https://dev.tariffia.app/data/provider-coins.json")
            buildConfigFieldString("MARKET_API_BASE_URL", "https://dev.tariffia.app/api")
            buildConfigFieldString("MARKET_API_KEY", secret("MARKET_API_KEY"))
            buildConfigFieldString("ALCHEMY_API_KEY", secret("ALCHEMY_API_KEY"))
            buildConfigFieldString("SOLANA_JUPITER_API_KEY", secret("SOLANA_JUPITER_API_KEY"))
            buildConfigFieldString("TONAPI_KEY", secret("TONAPI_KEY"))
            buildConfigFieldString("TRONGRID_API_KEYS", secret("TRONGRID_API_KEYS"))
            buildConfigFieldString("UDN_API_KEY", secret("UDN_API_KEY"))
            buildConfigFieldString("BLOCKS_DECODED_ETHEREUM_RPC", "https://ethereum-rpc.publicnode.com")
            buildConfigFieldString("CHAINALYSIS_BASE_URL", "https://public.chainalysis.com/api/v1/")
            buildConfigFieldString("CHAINALYSIS_API_KEY", secret("CHAINALYSIS_API_KEY"))
            buildConfigFieldString("HASH_DIT_BASE_URL", "https://service.hashdit.io/v2/hashdit/")
            buildConfigFieldString("HASH_DIT_API_KEY", secret("HASH_DIT_API_KEY"))
            buildConfigFieldString("USWAP_API_BASE_URL", "https://dev.tariffia.app/api/v3/")
            buildConfigFieldString("FASTNEAR_API_KEY", secret("FASTNEAR_API_KEY"))
        }

        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (requireReleaseSigning &&
                gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) } &&
                releaseSigningConfig == null
            ) {
                throw org.gradle.api.GradleException(
                    "Release signing is required but TARIFFIA_KEYSTORE_FILE, TARIFFIA_KEYSTORE_PASSWORD, " +
                        "TARIFFIA_KEY_ALIAS, or TARIFFIA_KEY_PASSWORD is missing."
                )
            }
            releaseSigningConfig?.let { signingConfig = it }
            buildConfigFieldString("APP_LINKS_HOST", "tariffia.app")
            manifestPlaceholders["appLinksHost"] = "tariffia.app"
            buildConfigFieldString("TWITTER_BEARER_TOKEN", secret("TWITTER_BEARER_TOKEN"))
            buildConfigFieldString("ETHERSCAN_KEY", secret("ETHERSCAN_KEY"))
            buildConfigFieldString("BSCSCAN_KEY", secret("BSCSCAN_KEY"))
            buildConfigFieldString("BLOCKSCOUT_KEY", secret("BLOCKSCOUT_KEY"))
            buildConfigFieldString("GUIDES_URL", "https://tariffia.app/guides/index.json")
            buildConfigFieldString("FAQ_URL", "https://tariffia.app/faq.json")
            buildConfigFieldString("COINS_JSON_URL", "https://tariffia.app/data/coins.json")
            buildConfigFieldString("PROVIDER_COINS_JSON_URL", "https://tariffia.app/data/provider-coins.json")
            buildConfigFieldString("MARKET_API_BASE_URL", "https://tariffia.app/api")
            buildConfigFieldString("MARKET_API_KEY", secret("MARKET_API_KEY"))
            buildConfigFieldString("ALCHEMY_API_KEY", secret("ALCHEMY_API_KEY"))
            buildConfigFieldString("SOLANA_JUPITER_API_KEY", secret("SOLANA_JUPITER_API_KEY"))
            buildConfigFieldString("TONAPI_KEY", secret("TONAPI_KEY"))
            buildConfigFieldString("TRONGRID_API_KEYS", secret("TRONGRID_API_KEYS"))
            buildConfigFieldString("UDN_API_KEY", secret("UDN_API_KEY"))
            buildConfigFieldString("BLOCKS_DECODED_ETHEREUM_RPC", "https://ethereum-rpc.publicnode.com")
            buildConfigFieldString("CHAINALYSIS_BASE_URL", "https://public.chainalysis.com/api/v1/")
            buildConfigFieldString("CHAINALYSIS_API_KEY", secret("CHAINALYSIS_API_KEY"))
            buildConfigFieldString("HASH_DIT_BASE_URL", "https://service.hashdit.io/v2/hashdit/")
            buildConfigFieldString("HASH_DIT_API_KEY", secret("HASH_DIT_API_KEY"))
            buildConfigFieldString("USWAP_API_BASE_URL", "https://tariffia.app/api/v3/")
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
