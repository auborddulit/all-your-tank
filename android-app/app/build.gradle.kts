plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Numéro de version : celui de la fabrication GitHub, pour que chaque nouvelle
// version puisse s'installer par-dessus la précédente.
val numeroVersion = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "fr.allyourtank.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "fr.allyourtank.app"
        minSdk = 26          // Android 8.0 et plus récent
        targetSdk = 34
        versionCode = numeroVersion
        versionName = "1.0.$numeroVersion"
    }

    // Clé de signature partagée : toutes les versions sont signées pareil,
    // donc une mise à jour s'installe sans désinstaller l'ancienne.
    // Ce n'est pas un secret : l'appli n'est pas sur le Play Store.
    signingConfigs {
        create("partage") {
            storeFile = file("appli.keystore")
            storePassword = "allyourtank"
            keyAlias = "appli"
            keyPassword = "allyourtank"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("partage")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // Les vérifications du Play Store ne concernent pas une appli installée à la main
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
