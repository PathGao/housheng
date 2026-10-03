plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "io.github.pathgao.housheng"
    compileSdk = 35
    sourceSets.getByName("androidTest").java.srcDir("../test-support")
    defaultConfig {
        applicationId = "io.github.pathgao.housheng"
        minSdk = 30
        targetSdk = 35
        versionCode = 5
        versionName = "0.2.3-clef-preview"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // offline 不带网络权限；online 加 Clef 与真实应用筛选，包名不同，两版可同时安装。
    flavorDimensions += "network"
    productFlavors {
        create("offline") { dimension = "network" }
        create("online") { dimension = "network"; applicationIdSuffix = ".online" }
    }
    // 发行签名只从环境变量读取，缺省时 assembleRelease 产出未签名包，CI 照常通过。
    val keystorePath = System.getenv("HOUSHENG_KEYSTORE_PATH")
    if (keystorePath != null) {
        signingConfigs.create("release") {
            storeFile = file(keystorePath)
            storePassword = System.getenv("HOUSHENG_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("HOUSHENG_KEY_ALIAS")
            keyPassword = System.getenv("HOUSHENG_KEY_PASSWORD")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
