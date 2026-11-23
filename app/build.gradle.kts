import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.room)
}

// Push con FCM (RF-29): solo si se agregó app/google-services.json (proyecto de Firebase).
// Sin él la app compila igual y los avisos llegan por Realtime y por la revisión periódica.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

// Claves de Supabase: se leen de local.properties (que NO se sube a GitHub).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.udenar.petcare"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.udenar.petcare"
        minSdk = 26 // RNF-06
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        // Ejecutor propio: arranca la app de pruebas de Hilt en el teléfono
        testInstrumentationRunner = "com.udenar.petcare.HiltTestRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${localProperties.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${localProperties.getProperty("SUPABASE_PUBLISHABLE_KEY", "")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    // Esquemas versionados en git: permiten migraciones automáticas entre versiones
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // AndroidX + ciclo de vida (ViewModel = capa de presentación del MVVM)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.kotlinx.serialization.json)

    // Base de datos local y sincronización en segundo plano
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Supabase
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime) // cambios en vivo entre miembros (WebSocket con OkHttp)

    // Imágenes (fotos de mascotas desde archivos locales)
    implementation(libs.coil.compose)

    // QR (RF-18, RF-19)
    implementation(libs.zxing.core)
    implementation(libs.play.services.code.scanner)

    // Paseos con GPS (RF-12)
    implementation(libs.maplibre.android)
    implementation(libs.play.services.location)

    // Avisos push (RF-29)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.ktor.client.okhttp)

    // Inyección de dependencias (Hilt)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Pruebas instrumentadas: ./gradlew connectedDebugAndroidTest (o "Run" sobre la clase en Android Studio)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
