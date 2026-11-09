plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.streamshield"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.streamshield"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.1.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}

val buildRust by tasks.registering(Exec::class) {
    workingDir = rootDir
    if (System.getProperty("os.name").startsWith("Windows")) {
        commandLine("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "scripts/build-native.ps1")
    } else {
        commandLine("bash", "scripts/build-native.sh")
    }
    inputs.dir(rootProject.file("native/src"))
    inputs.file(rootProject.file("native/Cargo.toml"))
    inputs.file(rootProject.file("native/Cargo.lock"))
    inputs.file(rootProject.file("native/rust-toolchain.toml"))
    inputs.file(rootProject.file("vendor/adblock-rust-master/Cargo.toml"))
    inputs.files(rootProject.file("scripts/build-native.ps1"), rootProject.file("scripts/build-native.sh"))
    inputs.dir(rootProject.file("vendor/adblock-rust-master/src"))
    outputs.dir("src/main/jniLibs")
}
tasks.named("preBuild") { dependsOn(buildRust) }

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.webkit:webkit:1.14.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
