plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "id.cachet.wallet.android"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "id.cachet.wallet.android"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Base URL for the issuance gateway — override per build type or via gradle property
        buildConfigField("String", "CACHET_BASE_URL", "\"http://10.0.2.2:8090\"")
    }

    flavorDimensions += "mode"
    productFlavors {
        create("demo") {
            dimension = "mode"
            applicationIdSuffix = ".demo"
            buildConfigField("boolean", "DEMO_ENABLED", "true")
        }
        create("prod") {
            dimension = "mode"
            buildConfigField("boolean", "DEMO_ENABLED", "false")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("src/androidTest/assets")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation(project(":shared"))

    // Use Compose BOM to manage versions
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // DateTime and Serialization (already included in shared module but needed for Android-specific code)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqldelight.android.driver)

    // QR code generation (encoding only, no camera/scanner)
    implementation(libs.zxing.core)

    // CameraX for QR scanning
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.concurrent.futures.ktx)
    // Guava is on runtime classpath (via ZXing/Tink) but CameraX 1.6 exposes
    // ListenableFuture in its API — need it at compile time too
    compileOnly("com.google.guava:guava:33.3.1-android")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.cucumber.android)
    androidTestImplementation(libs.cucumber.junit)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Generate network_security_config.xml at build time with static base IPs + detected local IP.
// Output goes to a generated res directory so the source file is never modified.
val generatedResDir = layout.buildDirectory.dir("generated/res/networkSecurity")

tasks.register("generateNetworkSecurityConfig") {
    description = "Generates network_security_config.xml with base IPs + detected local IP"
    group = "android"

    outputs.dir(generatedResDir)

    doLast {
        val xmlDir = generatedResDir.get().dir("xml").asFile
        xmlDir.mkdirs()

        // Base IPs that every developer needs (emulator loopback + localhost)
        val domains = mutableListOf("10.0.2.2", "localhost", "127.0.0.1")

        // Detect local IP for physical-device / adb-over-wifi testing
        val osName = System.getProperty("os.name").lowercase()
        val getIpCommand = when {
            osName.contains("windows") -> listOf("powershell", "-Command", "(Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias 'Wi-Fi' | Where-Object {\$_.IPAddress -like '192.168.*' -or \$_.IPAddress -like '10.*' -or \$_.IPAddress -like '172.*'}).IPAddress")
            osName.contains("mac") || osName.contains("darwin") -> listOf("bash", "-c", "ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo ''")
            else -> listOf("bash", "-c", "ip route get 8.8.8.8 2>/dev/null | grep -oP 'src \\K[\\d.]+' || hostname -I 2>/dev/null | awk '{print \$1}' || echo ''")
        }

        try {
            val process = ProcessBuilder(getIpCommand).redirectErrorStream(true).start()
            val localIP = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0 && localIP.matches("\\d+\\.\\d+\\.\\d+\\.\\d+".toRegex())) {
                domains.add(localIP)
                println("Network security config: added local IP $localIP")
            }
        } catch (e: Exception) {
            println("Network security config: could not detect local IP (${e.message})")
        }

        val domainEntries = domains.joinToString("\n") {
            "        <domain includeSubdomains=\"false\">$it</domain>"
        }

        File(xmlDir, "network_security_config.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
$domainEntries
    </domain-config>
</network-security-config>
"""
        )
    }
}

// Wire generated res into all variants and run before resource merging
android.applicationVariants.configureEach {
    registerGeneratedResFolders(generatedResDir.map { files(it) })
}

afterEvaluate {
    tasks.findByName("preBuild")?.dependsOn("generateNetworkSecurityConfig")
}
