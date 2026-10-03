import java.util.Properties

plugins {
    id("com.android.application")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localProperty(name: String, defaultValue: String = ""): String =
    localProperties.getProperty(name, defaultValue)

fun quotedBuildConfig(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "org.opentreenap.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.opentreenap.mobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 13
        versionName = "0.7.2"

        manifestPlaceholders["MAPS_API_KEY"] = localProperty("MAPS_API_KEY")
        buildConfigField("String", "OTM_BASE_URL", quotedBuildConfig(localProperty("OTM_BASE_URL")))
        buildConfigField("String", "OTM_INSTANCE", quotedBuildConfig(localProperty("OTM_INSTANCE", "napoli")))
        buildConfigField("String", "OTM_ACCESS_KEY", quotedBuildConfig(localProperty("OTM_ACCESS_KEY")))
        buildConfigField("String", "OTM_SECRET_KEY", quotedBuildConfig(localProperty("OTM_SECRET_KEY")))
        buildConfigField(
            "String",
            "BOTANICAL_MANIFEST_URL",
            quotedBuildConfig(
                localProperty(
                    "BOTANICAL_MANIFEST_URL",
                    "https://opentreenap.altervista.org/wp-json/opentreenap/v1/botanical-images"
                )
            )
        )
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-maps:20.0.0")
    implementation("com.google.maps.android:android-maps-utils:4.5.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.ar:core:1.56.0")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.activity:activity:1.12.3")
}
