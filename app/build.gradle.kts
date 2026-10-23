plugins { id("com.android.application") }
android {
    enableKotlin = false
    namespace = "dev.ytmlyrics"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.ytmlyrics"
        minSdk = 36
        targetSdk = 36
        versionCode = 10
        versionName = "0.6.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs.getByName("debug") {
        // Keep local upgrades on the original key; fresh clones and CI use
        // Android's standard debug signing instead of requiring a private file.
        val localKey = rootProject.file(".tooling/debug.keystore")
        if (localKey.isFile) storeFile = localKey
    }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
}
dependencies {
    implementation(project(":core"))
    compileOnly("io.github.libxposed:api:102.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}
