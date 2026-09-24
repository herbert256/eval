import java.util.Calendar
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Generate version from timestamp: yy.DDD.minutes (year.dayOfYear.minutesInDay)
val now = Calendar.getInstance()
val versionFromTimestamp: String = String.format("%02d.%d.%d",
    now.get(Calendar.YEAR) % 100,
    now.get(Calendar.DAY_OF_YEAR),
    now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE))

// Load keystore properties from local.properties
val keystoreProperties = Properties()
val keystoreFile = rootProject.file("local.properties")
if (keystoreFile.exists()) {
    keystoreProperties.load(keystoreFile.inputStream())
}

android {
    namespace = "com.eval"
    compileSdk = 34

    // Keep editable defaults at the repository root and package them as Android assets.
    sourceSets.getByName("main").assets.srcDir(rootProject.file("assets"))

    signingConfigs {
        create("release") {
            val ksFile = keystoreProperties["KEYSTORE_FILE"]?.toString()
            if (ksFile != null) {
                storeFile = rootProject.file(ksFile)
                storePassword = keystoreProperties["KEYSTORE_PASSWORD"]?.toString()
                keyAlias = keystoreProperties["KEY_ALIAS"]?.toString()
                keyPassword = keystoreProperties["KEY_PASSWORD"]?.toString()
            }
        }
    }

    defaultConfig {
        applicationId = "com.eval"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = versionFromTimestamp

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.retrofit.scalars)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Markdown rendering
    implementation(libs.compose.markdown)

    // Navigation
    implementation(libs.navigation.compose)

    // PDF text extraction on all supported Android versions. Page images use Android's renderer.
    implementation(libs.pdfbox.android)

    // Camera board scanning
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")

    debugImplementation(libs.androidx.ui.tooling)
}

val validateBundledPrompts by tasks.registering {
    val promptDirectories = listOf("assets/system_prompts", "assets/prompts")
    promptDirectories.forEach { inputs.dir(rootProject.file(it)) }
    doLast {
        promptDirectories.forEach { directory ->
            val promptFiles = rootProject.fileTree(directory) { include("*.json") }
            require(!promptFiles.isEmpty) { "No bundled prompts found in $directory" }
            promptFiles.forEach { file ->
                val prompt = groovy.json.JsonSlurper().parse(file) as? Map<*, *>
                require(prompt?.keys == setOf("title", "text") &&
                    prompt.values.all { it is String && it.isNotBlank() }) {
                    "${file.name} must have exactly two non-empty string fields: title and text"
                }
            }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(validateBundledPrompts) }
