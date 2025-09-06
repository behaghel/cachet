plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "id.cachet.wallet.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.cachet.wallet.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
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
        
        // Get local IP address using shell command
        val getIpCommand = if (System.getProperty("os.name").lowercase().contains("windows")) {
            listOf("powershell", "-Command", "(Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias 'Wi-Fi' | Where-Object {\$_.IPAddress -like '192.168.*' -or \$_.IPAddress -like '10.*' -or \$_.IPAddress -like '172.*'}).IPAddress")
        } else {
            listOf("bash", "-c", "ip route get 8.8.8.8 | grep -oP 'src \\K[\\d.]+'")
        }
        
        try {
            val process = ProcessBuilder(getIpCommand)
                .redirectErrorStream(true)
                .start()
            
            val localIP = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            
            if (exitCode == 0 && localIP.isNotEmpty() && localIP.matches("\\d+\\.\\d+\\.\\d+\\.\\d+".toRegex())) {
                println("Detected local IP: $localIP")
                
                if (networkConfigFile.exists()) {
                    val content = networkConfigFile.readText()
                    
                    // Check if IP is already present
                    if (!content.contains("<domain includeSubdomains=\"false\">$localIP</domain>")) {
                        // Add the IP to the domain-config section
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
                println("⚠️ Could not detect local IP address (got: '$localIP')")
                println("You may need to manually add your IP to network_security_config.xml")
            }
        } catch (e: Exception) {
            println("⚠️ Error detecting IP: ${e.message}")
            println("You may need to manually add your IP to network_security_config.xml")
        }
    }
}

// Hook into pre-build tasks to auto-update network config
afterEvaluate {
    tasks.findByName("preBuild")?.dependsOn("updateNetworkSecurityConfig")
    tasks.findByName("preDebugBuild")?.dependsOn("updateNetworkSecurityConfig")
    tasks.findByName("preReleaseBuild")?.dependsOn("updateNetworkSecurityConfig")
}