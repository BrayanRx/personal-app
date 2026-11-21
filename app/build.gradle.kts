plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "pe.aura.wallpaper"
    compileSdk = 35

    defaultConfig {
        applicationId = "pe.aura.wallpaper"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.4.0"
    }

    // Firma de release: la clave vive en los secrets de GitHub, nunca en el repo.
    // CI decodifica el keystore y pasa estas variables de entorno.
    val releaseKeystore = System.getenv("AURA_KEYSTORE_PATH")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("AURA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AURA_KEY_ALIAS")
                keyPassword = System.getenv("AURA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
            resValue("string", "app_name", "Aura")
        }
        debug {
            // Las builds de prueba se instalan aparte y no chocan con la versión publicada.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Aura Dev")
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
        resValues = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
