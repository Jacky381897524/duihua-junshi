import java.security.KeyStore
import java.security.Signature
import java.security.MessageDigest
import java.util.Base64
plugins { id("com.android.application"); kotlin("android") }
val authorAssets=layout.buildDirectory.dir("generated/verifiedAuthorAssets")
val prepareAuthorIdentity=tasks.register("prepareAuthorIdentity") {
    outputs.dir(authorAssets)
    outputs.upToDateWhen { false }
    doLast {
        val folder=authorAssets.get().asFile.resolve("verified-author");folder.mkdirs()
        val avatar=System.getenv("LOVEADVISOR_AUTHOR_AVATAR")?.let(::file) ?: file("../../private-signing/author-avatar.jpg")
        val store=System.getenv("LOVEADVISOR_SIGNING_STORE")?.let(::file) ?: file("../../private-signing/love-advisor-dev.p12")
        val authorName=avatar.parentFile.resolve("author-name.txt")
        if(!avatar.exists() || !store.exists() || !authorName.exists()) {
            folder.resolve("identity.txt").delete();folder.resolve("avatar.jpg").delete()
        } else {
            val keys=KeyStore.getInstance("PKCS12").apply { store.inputStream().use { load(it,"android".toCharArray()) } }
            val key=keys.getKey("love-advisor-debug","android".toCharArray()) as java.security.PrivateKey
            val cert=keys.getCertificate("love-advisor-debug").encoded
            val avatarDigest=MessageDigest.getInstance("SHA-256").digest(avatar.readBytes()).joinToString("") { "%02x".format(it) }
            val name=authorName.readText().trim();require(name.length in 1..80 && !name.contains('\n'))
            val payload="author-v1\n$name\n$avatarDigest\n".toByteArray(Charsets.UTF_8)
            val signature=Signature.getInstance("SHA256withRSA").apply { initSign(key);update(payload) }.sign()
            val b64=Base64.getEncoder()
            folder.resolve("identity.txt").writeText(listOf(payload,signature,cert).joinToString("\n") { b64.encodeToString(it) })
            avatar.copyTo(folder.resolve("avatar.jpg"),overwrite=true)
        }
    }
}
android {
    namespace = "com.loveadvisor.android"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.loveadvisor.android"
        minSdk = 30
        targetSdk = 35
        versionCode = 50
        versionName = "V1.7.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    sourceSets.getByName("main").assets.srcDir(authorAssets)
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
tasks.named("preBuild").configure { dependsOn(prepareAuthorIdentity) }
dependencies {
    implementation(project(":core"))
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
