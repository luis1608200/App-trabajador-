plugins { id("com.android.application") }
android {
 namespace = "pe.apptrabajador"
 compileSdk = 35
 defaultConfig {
  applicationId = "pe.apptrabajador"
  minSdk = 26
  targetSdk = 35
  versionCode = 2
  versionName = "0.2.0-beta.1"
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 }
 buildTypes { getByName("debug") { applicationIdSuffix = ".beta" } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 testOptions { animationsDisabled = true }
}
dependencies {
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test:rules:1.6.1")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
