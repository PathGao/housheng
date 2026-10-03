plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "io.github.pathgao.housheng"
    compileSdk = 35
    sourceSets.getByName("androidTest").java.srcDir("../test-support")
    defaultConfig {
        applicationId = "io.github.pathgao.housheng"
        minSdk = 30
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.1-family-preview"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
