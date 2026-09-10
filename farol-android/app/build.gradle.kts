plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.farol.corrida"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.farol.corrida"
        minSdk = 26
        targetSdk = 35
        // Cada build do CI vira uma versao maior, para o Android aceitar a
        // atualizacao por cima da anterior.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    signingConfigs {
        // Chave fixa de desenvolvimento, versionada de proposito. Sem ela cada
        // build sairia com uma assinatura diferente e o Android exigiria
        // desinstalar o app (perdendo as permissoes) a cada atualizacao.
        // Nao e uma chave de publicacao em loja e nao protege nada sigiloso.
        create("shared") {
            storeFile = file("../farol-dev.keystore")
            storePassword = "farolapp"
            keyAlias = "farol"
            keyPassword = "farolapp"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shared")
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}
