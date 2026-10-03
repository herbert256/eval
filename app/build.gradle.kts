import java.util.Calendar
import java.util.Locale
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Generate version from timestamp: yy.DDD.minutes (year.dayOfYear.minutesInDay)
val now = Calendar.getInstance()
val versionFromTimestamp: String = String.format(Locale.ROOT, "%02d.%d.%d",
    now.get(Calendar.YEAR) % 100,
    now.get(Calendar.DAY_OF_YEAR),
    now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE))
// Same timestamp as a monotonically increasing code, so a newer build always has a higher
// versionCode (Android refuses to install an older code over a newer one).
val versionCodeFromTimestamp: Int = ((now.get(Calendar.YEAR) % 100) * 1000 + now.get(Calendar.DAY_OF_YEAR)) * 1440 +
    now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

// Load keystore properties from local.properties
val keystoreProperties = Properties()
val keystoreFile = rootProject.file("local.properties")
if (keystoreFile.exists()) {
    keystoreProperties.load(keystoreFile.inputStream())
}

android {
    namespace = "com.eval"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    buildToolsVersion = "37.0.0"

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
        targetSdk = 37
        versionCode = versionCodeFromTimestamp
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
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // JVM tests exercise repository code that logs through android.util.Log.
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // BouncyCastle (via pdfbox-android, only used to decrypt PDFs) ships ~8 MB of
            // post-quantum parameter tables the app never uses.
            excludes += "org/bouncycastle/pqc/**"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
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

    // Navigation
    implementation(libs.navigation.compose)

    // PDF text extraction on all supported Android versions. Page images use Android's renderer.
    implementation(libs.pdfbox.android)

    // Gson is used directly (models, storage), so it tracks its own release, as in the AI app.
    implementation(libs.gson)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)

    debugImplementation(libs.androidx.ui.tooling)
}

val validateBundledPrompts = tasks.register("validateBundledPrompts") {
    val promptDirectories = listOf("assets/system_prompts", "assets/prompts")
    promptDirectories.forEach { inputs.dir(rootProject.file(it)) }
    // A marker output lets Gradle skip the check while the prompts are unchanged.
    val marker = layout.buildDirectory.file("validateBundledPrompts/ok")
    outputs.file(marker)
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
        marker.get().asFile.apply { parentFile.mkdirs(); writeText("ok") }
    }
}
tasks.named("preBuild").configure { dependsOn(validateBundledPrompts) }
