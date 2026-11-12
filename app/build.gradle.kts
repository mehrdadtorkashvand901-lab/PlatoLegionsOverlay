plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "ir.legions.overlay"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "ir.legions.overlay"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
    }
    ndkVersion = "27.2.12479018"
    signingConfigs.getByName("debug") { storeFile = rootProject.file(".gradle/debug.keystore") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = false } }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint { abortOnError = true }
}
val debugKey = rootProject.file(".gradle/debug.keystore")
val prepareDebugKey by tasks.registering(Exec::class) {
    onlyIf { !debugKey.exists() }
    doFirst { debugKey.parentFile.mkdirs() }
    commandLine(File(System.getProperty("java.home"), "bin/keytool"),
        "-genkeypair", "-keystore", debugKey, "-storepass", "android", "-keypass", "android",
        "-alias", "androiddebugkey", "-dname", "CN=Android Debug,O=Android,C=US",
        "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-noprompt")
}
tasks.matching { it.name == "validateSigningDebug" }.configureEach { dependsOn(prepareDebugKey) }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
