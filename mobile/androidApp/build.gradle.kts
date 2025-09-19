import groovy.json.JsonSlurper
import java.net.Inet4Address
import java.net.NetworkInterface

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val configPathFromEnv = System.getenv("CACHET_CONFIG_PATH")
val appConfigFile = if (configPathFromEnv != null && configPathFromEnv.isNotBlank()) {
    file(configPathFromEnv)
} else {
    rootProject.file("../config/app-config.json")
}
val appConfig = JsonSlurper().parse(appConfigFile) as Map<*, *>
val defaultEnvironment = (appConfig["defaultEnvironment"] as String?) ?: "local"
val cachetEnv: String = (project.findProperty("cachetEnv") as String?) ?: defaultEnvironment
val environments = appConfig["environments"] as? Map<*, *>
    ?: throw GradleException("config/app-config.json missing environments block")
val environmentBlock = environments[cachetEnv] as? Map<*, *>
    ?: throw GradleException("Environment '$cachetEnv' not defined in config/app-config.json")
val servicesConfig = environmentBlock["services"] as? Map<*, *>
    ?: throw GradleException("Environment '$cachetEnv' missing services configuration")
val issuanceGatewayConfig = servicesConfig["issuanceGateway"] as? Map<*, *>
    ?: throw GradleException("Environment '$cachetEnv' missing issuanceGateway configuration")
val issuanceBaseUrlOverride = (project.findProperty("cachetIssuanceBaseUrl") as String?)
    ?: System.getenv("CACHET_ISSUANCE_BASE_URL")

fun detectLocalIp(): String? {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        val excludedPrefixes = listOf("docker", "br-", "veth", "virbr", "lo")
        val preferred = mutableListOf<String>()
        val fallback = mutableListOf<String>()

        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()

            val isUp = try {
                networkInterface.isUp
            } catch (_: Exception) {
                true
            }
            if (!isUp || networkInterface.isLoopback || networkInterface.isVirtual) continue

            val name = networkInterface.name.lowercase()
            val displayName = networkInterface.displayName?.lowercase() ?: ""
            val isExcluded = excludedPrefixes.any { prefix ->
                name.startsWith(prefix) || displayName.startsWith(prefix)
            }

            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && address.isSiteLocalAddress) {
                    val host = address.hostAddress
                    if (host.startsWith("169.254")) continue
                    if (isExcluded) {
                        fallback.add(host)
                    } else {
                        preferred.add(host)
                    }
                }
            }
        }

        val candidate = (preferred + fallback).firstOrNull()
        if (candidate != null) {
            return candidate
        }

        val fallbackCommand = if (System.getProperty("os.name").lowercase().contains("windows")) {
            listOf("powershell", "-Command", "(Get-NetIPAddress -AddressFamily IPv4 | Where-Object {\$_.IPAddress -like '192.168.*' -or \$_.IPAddress -like '10.*' -or \$_.IPAddress -like '172.*'} | Select-Object -First 1 -ExpandProperty IPAddress)")
        } else {
            listOf("bash", "-c", "ip route get 8.8.8.8 | grep -oP 'src \\K[\\d.]+'");
        }

        return try {
            val process = ProcessBuilder(fallbackCommand)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (output.matches("\\d+\\.\\d+\\.\\d+\\.\\d+".toRegex())) output else null
        } catch (_: Exception) {
            null
        }
    } catch (e: Exception) {
        null
    }
}

val detectedLocalIp = detectLocalIp()

val issuanceBaseUrl = issuanceBaseUrlOverride
    ?: detectedLocalIp?.let { "http://$it:8090" }
    ?: (issuanceGatewayConfig["emulatorUrl"] ?: issuanceGatewayConfig["publicUrl"]) as? String
    ?: throw GradleException("issuanceGateway configuration for '$cachetEnv' missing emulatorUrl/publicUrl")

repositories {
    google()
    mavenCentral()
    // Veriff SDK repository
    maven { 
        url = uri("https://cdn.veriff.me/android/")
        isAllowInsecureProtocol = false
    }
}

android {
    namespace = "id.cachet.wallet.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.cachet.wallet.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "CACHET_ENV", "\"$cachetEnv\"")
        buildConfigField("String", "ISSUANCE_BASE_URL", "\"$issuanceBaseUrl\"")
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
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":shared"))
    
    // Use Compose BOM to manage versions
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("io.insert-koin:koin-android:3.5.3")
    implementation("io.insert-koin:koin-androidx-compose:3.5.3")
    
    // Veriff SDK for identity verification
    implementation("com.veriff:veriff-library:7.9.1")
    
    // HTTP client for Veriff API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // DateTime and Serialization (already included in shared module but needed for Android-specific code)
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("app.cash.sqldelight:android-driver:2.0.1")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Task to automatically update network security config with local development IP
tasks.register("updateNetworkSecurityConfig") {
    description = "Updates network_security_config.xml with the current machine's IP address"
    group = "android"
    
    doLast {
        val networkConfigFile = file("src/main/res/xml/network_security_config.xml")
        val localIP = detectedLocalIp ?: detectLocalIp()

        if (localIP != null && localIP.matches("\\\\d+\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+".toRegex())) {
            println("Detected local IP: $localIP")

            if (networkConfigFile.exists()) {
                val content = networkConfigFile.readText()

                if (!content.contains("<domain includeSubdomains=\"false\">$localIP</domain>")) {
                    val updatedContent = content.replace(
                        "</domain-config>",
                        "        <domain includeSubdomains=\"false\">$localIP</domain>\n    </domain-config>"
                    )

                    networkConfigFile.writeText(updatedContent)
                    println("✅ Updated network_security_config.xml with IP: $localIP")
                } else {
                    println("✅ IP $localIP already present in network_security_config.xml")
                }
            } else {
                println("❌ network_security_config.xml not found")
            }
        } else {
            println("⚠️ Could not detect local IP address; ensure network_security_config.xml includes your host")
        }
    }
}

// Hook into pre-build tasks to auto-update network config
afterEvaluate {
    tasks.findByName("preBuild")?.dependsOn("updateNetworkSecurityConfig")
    tasks.findByName("preDebugBuild")?.dependsOn("updateNetworkSecurityConfig")
    tasks.findByName("preReleaseBuild")?.dependsOn("updateNetworkSecurityConfig")
}
