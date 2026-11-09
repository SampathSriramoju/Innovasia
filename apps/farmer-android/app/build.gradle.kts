import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.bolkisan.farmer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bolkisan.farmer"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val devBackendIp = (project.findProperty("devBackendIp") as? String) ?: "10.0.2.2"
        buildConfigField("String", "DEV_BACKEND_IP", "\"$devBackendIp\"")
        buildConfigField("String", "PROD_API_BASE_URL", "\"https://api.bolkisan.gov.in/api/v1/\"")
        buildConfigField("String", "DEFAULT_API_BASE_URL", "\"http://$devBackendIp:8000/api/v1/\"")
    }

    buildTypes {
        debug {
            val devBackendIp = (project.findProperty("devBackendIp") as? String) ?: "10.0.2.2"
            buildConfigField("Boolean", "IS_PRODUCTION", "false")
            buildConfigField("String", "CONFIGURED_API_BASE_URL", "\"http://$devBackendIp:8000/api/v1/\"")
        }
        release {
            buildConfigField("Boolean", "IS_PRODUCTION", "true")
            buildConfigField("String", "CONFIGURED_API_BASE_URL", "\"https://api.bolkisan.gov.in/api/v1/\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    androidResources {
        noCompress += listOf("litertlm", "bin", "json")
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", layout.buildDirectory.dir("generated/model-assets"))
        }
    }
}

val generatedModelAssetsDir = layout.buildDirectory.dir("generated/model-assets")

val prepareOfflineModelAsset by tasks.registering {
    val modelDir = generatedModelAssetsDir.get().asFile.resolve("models")
    val targetFile = modelDir.resolve("bolkisan_qwen3_0_6b_int4.litertlm")

    outputs.file(targetFile)

    doLast {
        modelDir.mkdirs()
        val expectedSha = "bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0"
        val expectedSize = 343490560L
        val downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/bd47201734a34f1eba8ed7d28289225b60152e9e/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"

        fun calculateSha256(f: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { fis: InputStream ->
                val buf = ByteArray(128 * 1024)
                var read: Int
                while (fis.read(buf).also { read = it } != -1) {
                    digest.update(buf, 0, read)
                }
            }
            return digest.digest().joinToString("") { b: Byte -> "%02x".format(b) }
        }

        // 1. Check local model path from Gradle property or environment variable
        val customPath = project.findProperty("bolkisan.model.path") as? String
            ?: System.getenv("BOLKISAN_MODEL_PATH")

        val userCache = File(System.getProperty("user.home"), ".bolkisan/models/bolkisan_qwen3_0_6b_int4.litertlm")

        val sourceFile: File = when {
            customPath != null && File(customPath).exists() -> File(customPath)
            userCache.exists() -> userCache
            else -> {
                println("Downloading pinned INT4 LiteRT-LM model to user cache: ${userCache.absolutePath}")
                userCache.parentFile.mkdirs()
                val tempDownload = File(userCache.parentFile, "bolkisan_qwen3_0_6b_int4.litertlm.tmp")
                val url = URL(downloadUrl)
                val conn = url.openConnection()
                conn.setRequestProperty("User-Agent", "BolKisan-BuildTool/1.0")
                conn.getInputStream().use { input: InputStream ->
                    tempDownload.outputStream().use { output: OutputStream ->
                        input.copyTo(output)
                    }
                }
                tempDownload.renameTo(userCache)
                userCache
            }
        }

        if (!sourceFile.exists()) {
            throw GradleException("Offline SLM model artifact not found: ${sourceFile.absolutePath}")
        }

        if (sourceFile.length() != expectedSize) {
            throw GradleException("Offline SLM model size mismatch: expected $expectedSize bytes, found ${sourceFile.length()}")
        }

        val hash = calculateSha256(sourceFile)
        if (!hash.equals(expectedSha, ignoreCase = true)) {
            throw GradleException("Offline SLM model SHA-256 mismatch: expected $expectedSha, found $hash")
        }

        // Copy to generated assets directory
        if (!targetFile.exists() || targetFile.length() != expectedSize) {
            println("Packaging verified model artifact into assets: ${targetFile.absolutePath}")
            try {
                if (targetFile.exists() && targetFile.length() != expectedSize) {
                    targetFile.delete()
                }
                sourceFile.copyTo(targetFile, overwrite = true)
            } catch (e: Exception) {
                if (targetFile.exists() && targetFile.length() == expectedSize) {
                    println("Target file already intact with expected size ($expectedSize bytes).")
                } else {
                    throw e
                }
            }
        }
        println("Verified model ready for packaging: ${targetFile.name} (${targetFile.length()} bytes, SHA-256: $expectedSha)")
    }
}

tasks.matching {
    it.name != "prepareOfflineModelAsset" && (
        (it.name.startsWith("merge") && it.name.endsWith("Assets")) ||
        it.name.contains("Lint") ||
        it.name.contains("Assets")
    )
}.configureEach {
    dependsOn(prepareOfflineModelAsset)
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
        force("org.jetbrains.kotlin:kotlin-reflect:1.9.24")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // CameraX
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // WorkManager (Low-Connectivity Background Sync)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Google LiteRT-LM Official On-Device SLM Runtime
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.1") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-android")
    }
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.24")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
