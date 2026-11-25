import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firma de la versión publicada: datos en keystore.properties (nunca se sube a git).
// Se busca en la raíz del proyecto y, si no está, en Documentos/claves (compartido con otras apps).
val signingProps: Properties? = listOf(
    rootProject.file("keystore.properties"),
    File(System.getProperty("user.home"), "Documents/claves/keystore.properties"),
).firstOrNull { it.isFile }?.let { f -> Properties().apply { f.reader(Charsets.UTF_8).use { load(it) } } }

android {
    signingConfigs {
        if (signingProps != null) create("release") {
            storeFile = file(signingProps.getProperty("storeFile"))
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias")
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }

    namespace = "io.github.neoruben.lidlstats"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.neoruben.lidlstats"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// APK publicada: LidlStats-vX.Y.Z.apk (misma convención que MercaStats o PayFilter).
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            (output as com.android.build.api.variant.impl.VariantOutputImpl).outputFileName.set(
                output.versionName.map { "LidlStats-v$it.apk" }
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jsoup:jsoup:1.18.3")

    testImplementation("junit:junit:4.13.2")
}
