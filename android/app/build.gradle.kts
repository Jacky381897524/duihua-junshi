plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "com.loveadvisor.android"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.loveadvisor.android"
        minSdk = 30
        targetSdk = 35
        versionCode = 46
        versionName = "V1.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    packaging { jniLibs { useLegacyPackaging = false } }
    signingConfigs { getByName("debug") {
        val configuredStore=System.getenv("LOVEADVISOR_SIGNING_STORE")
        val localStore=file("../../private-signing/love-advisor-dev.p12")
        // A clean clone uses Android's standard local debug key. Published updates
        // are built with the author's private key supplied outside the repository.
        if(configuredStore!=null || localStore.exists()) {
            storeFile=if(configuredStore!=null)file(configuredStore) else localStore
            storePassword="android";keyAlias="love-advisor-debug";keyPassword="android";storeType="PKCS12"
        }
    } }
    buildTypes { release { isMinifyEnabled = false } }
}
dependencies {
    implementation(project(":core"))
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
