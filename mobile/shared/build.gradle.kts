plugins {
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
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
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.logging)
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)
                implementation(libs.koin.core)
                implementation(libs.multiplatform.settings)
                implementation(libs.multiplatform.settings.coroutines)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // Real crypto libs for JWSVerifier/JWEEncryptor round-trip tests
                implementation(libs.nimbus.jose.jwt)
                implementation(libs.bouncycastle)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.ktor.client.android)
                implementation(libs.sqldelight.android.driver)
                implementation(libs.kotlinx.coroutines.android)
                // JWE/JWS for E2E encryption and signed request verification
                implementation(libs.nimbus.jose.jwt)
                implementation(libs.tink.android)
                implementation(libs.bouncycastle)
            }
        }
        val iosMain by getting {
            dependencies {
                implementation(libs.ktor.client.ios)
                implementation(libs.sqldelight.native.driver)
            }
        }
    }
}

android {
    namespace = "id.cachet.wallet.shared"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

sqldelight {
    databases {
        create("WalletDatabase") {
            packageName.set("id.cachet.wallet.db")
        }
    }
}
