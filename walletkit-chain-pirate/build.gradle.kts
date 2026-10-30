plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "io.horizontalsystems.walletkit.chain.pirate"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":walletkit"))

    // Zcash SDK — Pirate Chain is a Zcash fork (Sapling/Ironwood shielded pools).
    // Reuse the same gRPC + librustzcash stack; network params are configured in PirateRpcSourceManager.
    api(libs.zcash.android.sdk)
    implementation(libs.zcash.android.backend)
    implementation(libs.zcash.android.slipstream)
    implementation(libs.zcash.android.lightwallet.client)
    implementation("com.google.protobuf:protobuf-javalite:4.34.0")

    testImplementation(libs.junit)
}
