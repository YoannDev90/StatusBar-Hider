import java.util.Properties

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlin.serialization)
}

// Release signing config: reads keystore.properties (gitignored).
// Without this file, release builds remain unsigned.
val keystoreProps = rootProject
	.file("keystore.properties")
	.takeIf { it.exists() }
	?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

android {
	namespace = "dev.yoanndev90.statusbarhider"
	compileSdk = 37

	defaultConfig {
		applicationId = "dev.yoanndev90.statusbarhider"
		minSdk = 26
		targetSdk = 37
		versionCode = 1
		versionName = "1.0"
	}

	signingConfigs {
		if (keystoreProps != null) {
			create("release") {
				storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
				storePassword = keystoreProps.getProperty("storePassword")
				keyAlias = keystoreProps.getProperty("keyAlias")
				keyPassword = keystoreProps.getProperty("keyPassword")
			}
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
			if (keystoreProps != null) {
				signingConfig = signingConfigs.getByName("release")
			}
		}
		debug {
			isMinifyEnabled = false
			isShrinkResources = false
			applicationIdSuffix = ".debug"
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	buildFeatures {
		compose = true
	}
}

dependencies {
	// Compose BOM keeps all androidx.compose versions in sync.
	implementation(platform(libs.compose.bom))
	implementation(libs.activity.compose)
	implementation(libs.activity.ktx)
	implementation(libs.compose.foundation)
	implementation(libs.compose.material.icons.core)
	implementation(libs.compose.material3)
	implementation(libs.compose.ui)
	implementation(libs.compose.ui.tooling.preview)
	implementation(libs.lifecycle.runtime.compose)
	implementation(libs.lifecycle.runtime.ktx)
	implementation(libs.lifecycle.viewmodel.compose)
	implementation(libs.lifecycle.viewmodel.ktx)
	implementation(libs.reorderable)
	debugImplementation(libs.compose.ui.tooling)

	// Shizuku API 13.1.5 + aidl (newProcess is private in the api artifact;
	// we call IShizukuService.newProcess() directly via the aidl artifact).
	// https://github.com/RikkaApps/Shizuku-API
	implementation(libs.api)
	implementation(libs.aidl)
	implementation(libs.provider)

	testImplementation(libs.json)
	testImplementation(libs.junit)
}
