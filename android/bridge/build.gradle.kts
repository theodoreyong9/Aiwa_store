plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.aiwa.bridge"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    // Neither of these has a default that actually agrees with the
    // other: AGP's javac task defaults to 1.8 unless told otherwise,
    // while the Kotlin plugin defaults jvmTarget to whatever JDK is
    // running Gradle itself (17 here) — a real build break
    // (":bridge:compileDebugKotlin FAILED", "Inconsistent JVM Target
    // Compatibility") that only ever surfaces once Kotlin compilation
    // is actually reached, which the compileSdk 35 failure above always
    // prevented before this fix.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Ed25519, to check that the page is the one the site key signed (the platform has it only from Android 13)
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    testImplementation("junit:junit:4.13.2")
    // org.json as the phone has it: the unit tests run on a plain JVM, where the Android stub does nothing
    testImplementation("org.json:json:20250107")
}
