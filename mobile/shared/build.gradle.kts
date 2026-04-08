plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("app.cash.sqldelight") version "2.3.2"
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget {
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
            }
        }
    }
    
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation("io.ktor:ktor-client-core:2.3.7")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
                implementation("io.ktor:ktor-client-logging:2.3.7")
                implementation("app.cash.sqldelight:runtime:2.3.2")
                implementation("app.cash.sqldelight:coroutines-extensions:2.3.2")
                implementation("io.insert-koin:koin-core:3.5.3")
                implementation("com.russhwolf:multiplatform-settings:1.3.0")
                implementation("com.russhwolf:multiplatform-settings-coroutines:1.3.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // Real crypto libs for JWSVerifier/JWEEncryptor round-trip tests
                implementation("com.nimbusds:nimbus-jose-jwt:10.9")
                implementation("org.bouncycastle:bcprov-jdk15to18:1.83")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-android:2.3.7")
                implementation("app.cash.sqldelight:android-driver:2.3.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
                // JWE/JWS for E2E encryption and signed request verification
                implementation("com.nimbusds:nimbus-jose-jwt:10.9")
                implementation("com.google.crypto.tink:tink-android:1.21.0")
                implementation("org.bouncycastle:bcprov-jdk15to18:1.83")
            }
        }
        val iosMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-ios:2.3.7")
                implementation("app.cash.sqldelight:native-driver:2.3.2")
            }
        }
    }
}

android {
    namespace = "id.cachet.wallet.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
}

sqldelight {
    databases {
        create("WalletDatabase") {
            packageName.set("id.cachet.wallet.db")
        }
    }
}