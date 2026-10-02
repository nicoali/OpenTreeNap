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
        minSdk = 23
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"

        manifestPlaceholders["MAPS_API_KEY"] = localProperty("MAPS_API_KEY")
        buildConfigField("String", "OTM_BASE_URL", quotedBuildConfig(localProperty("OTM_BASE_URL")))
        buildConfigField("String", "OTM_INSTANCE", quotedBuildConfig(localProperty("OTM_INSTANCE", "napoli")))
        buildConfigField("String", "OTM_ACCESS_KEY", quotedBuildConfig(localProperty("OTM_ACCESS_KEY")))
        buildConfigField("String", "OTM_SECRET_KEY", quotedBuildConfig(localProperty("OTM_SECRET_KEY")))
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-maps:20.0.0")
}
