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

        // CucumberTestRunner extends CucumberAndroidJUnitRunner extends AndroidJUnitRunner
        // — runs both Cucumber .feature scenarios and regular JUnit4 instrumented tests
        testInstrumentationRunner = "id.cachet.wallet.android.bdd.CucumberTestRunner"
        testInstrumentationRunnerArguments["optionsAnnotationPackage"] = "id.cachet.wallet.android.bdd"

        // Cucumber-Android reads .feature files via AssetManager.list() which
        // fails if assets are compressed. Keep them uncompressed.
        androidResources {
            noCompress.add("feature")
        }
        vectorDrawables {
            useSupportLibrary = true
        }

        // Base URL for the issuance gateway.
        // Emulator uses 10.0.2.2 (host loopback alias); physical devices need the
        // host's LAN IP.  Pass -PcachetHost=<ip> to override.
        val cachetHost = project.findProperty("cachetHost")?.toString() ?: "10.0.2.2"
        buildConfigField("String", "CACHET_BASE_URL", "\"http://$cachetHost:8090\"")
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
            assets.directories.add("src/androidTest/assets")
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
    compileOnly("com.google.guava:guava:33.6.0-android")

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

// Generate network_security_config.xml at build time.
// The file is written to src/main/res/xml/ (gitignored) and fully regenerated
// each build — no stale IPs accumulate.
tasks.register("generateNetworkSecurityConfig") {
    description = "Generates network_security_config.xml with base IPs + detected local IP"
    group = "android"

    // Resolve path eagerly so doLast doesn't capture the build-script reference
    val xmlDir = layout.projectDirectory.dir("src/main/res/xml").asFile

    doLast {
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

// Copy BDD .feature files from spec/ into androidTest assets at build time.
// Source of truth is spec/{domain}/stories/{story}/scenarios.feature — each is
// renamed to {story}.feature so Cucumber finds them by story name.
tasks.register("copyBddFeatureFiles") {
    description = "Copies spec/ .feature files into androidTest assets"
    group = "android"

    val specRoot = rootProject.file("../spec")
    val outputDir = file("src/androidTest/assets/features")

    inputs.dir(specRoot)
    outputs.dir(outputDir)

    doLast {
        outputDir.mkdirs()
        // Clean stale copies
        outputDir.listFiles()?.filter { it.extension == "feature" }?.forEach { it.delete() }

        specRoot.walkTopDown()
            .filter { it.name == "scenarios.feature" }
            .forEach { featureFile ->
                // Parent dir name is the story name: spec/.../stories/first-launch/scenarios.feature → first-launch.feature
                val storyName = featureFile.parentFile.name
                featureFile.copyTo(File(outputDir, "$storyName.feature"), overwrite = true)
            }
        println("BDD features: copied ${outputDir.listFiles()?.count { it.extension == "feature" } ?: 0} files")
    }
}

afterEvaluate {
    tasks.findByName("preBuild")?.dependsOn("generateNetworkSecurityConfig", "copyBddFeatureFiles")
}
