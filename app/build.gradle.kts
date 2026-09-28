plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.serialization")
 id("org.jetbrains.kotlin.plugin.compose")
}
val copyRepoIcon by tasks.registering(Copy::class) {
 from(rootProject.file("grok_1789741084333.jpg"))
 into(layout.projectDirectory.dir("src/main/res/drawable"))
 rename { "miao_library_icon.jpg" }
}

tasks.named("preBuild").configure { dependsOn(copyRepoIcon) }

android {
 buildFeatures { compose=true; buildConfig=true }
 namespace="in.miaolibrary.cataloging"
 compileSdk=35
 defaultConfig {
  applicationId="in.miaolibrary.cataloging"
  minSdk=26
  targetSdk=35
  versionCode=1
  versionName="1.0"
  val geminiKey = providers.environmentVariable("GEMINI_API_KEY").orElse(providers.gradleProperty("GEMINI_API_KEY")).orElse("")
  buildConfigField("String", "GEMINI_API_KEY", "\\"$geminiKey\\")
 }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }

}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.01.00"))
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 implementation("androidx.camera:camera-camera2:1.4.1")
 implementation("androidx.camera:camera-lifecycle:1.4.1")
 implementation("androidx.camera:camera-view:1.4.1")
 implementation("com.google.mlkit:barcode-scanning:17.3.0")
 implementation("com.google.mlkit:text-recognition:16.0.1")
 implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
 implementation("com.google.android.gms:play-services-mlkit-language-id:17.0.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1")
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("org.jsoup:jsoup:1.18.3")
}
