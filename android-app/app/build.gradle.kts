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

    // Clé de signature : elle est gardée dans les « secrets » GitHub du dépôt
    // (ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD), jamais dans le code.
    // Le workflow la dépose dans ANDROID_KEYSTORE_FILE avant de fabriquer l'appli.
    // Toutes les versions sont signées pareil : une mise à jour s'installe
    // par-dessus l'ancienne.
    // Sans ces secrets (copie du projet, fabrication sur votre PC), l'appli est
    // signée avec la clé de test d'Android : elle s'installe, mais il faudra
    // désinstaller l'ancienne version à chaque changement de clé.
    val fichierCle = System.getenv("ANDROID_KEYSTORE_FILE")
    val motDePasseCle = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    val clePresente = !fichierCle.isNullOrBlank() && !motDePasseCle.isNullOrBlank()
    signingConfigs {
        if (clePresente) {
            create("brasserie") {
                storeFile = file(fichierCle!!)
                storePassword = motDePasseCle
                keyAlias = "appli"
                keyPassword = motDePasseCle
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (clePresente) "brasserie" else "debug")
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
