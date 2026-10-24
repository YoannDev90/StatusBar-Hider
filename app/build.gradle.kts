import java.util.Properties

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.detekt)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlin.serialization)
}

// Release signing config: reads keystore.properties (gitignored).
// Without this file, release builds remain unsigned.
val keystoreProps = rootProject
	.file("keystore.properties")
	.takeIf { it.exists() }
	?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

// Local version derivation, same scheme as the release workflow: the nearest
// tag plus the commits on top of it (v1.2.0-46-gf8af045 ->
// 1002046 / "1.2.0-dev.46+gf8af045"), so a local release installs over a CI
// build of the same tag and still tells itself apart. Queried through
// providers.exec, so the configuration cache recomputes it on a new commit.
// No git or no vX.Y.Z tag falls back to the placeholder pair.
val gitVersion: Pair<Int, String> =
	runCatching {
		providers
			.exec {
				commandLine("git", "describe", "--tags", "--long")
			}.standardOutput.asText
			.get()
			.trim()
	}.getOrNull()
		?.let { describe ->
			Regex("""^v(\d+)\.(\d+)\.(\d+)-(\d+)-g([0-9a-f]+)$""").find(describe)?.let { match ->
				val (major, minor, patch, commits, sha) = match.destructured
				val base = major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt()
				// One bump per commit; a fresh tag jumps to its own base again.
				val code = base + commits.toInt()
				val name =
					if (commits == "0") {
						"$major.$minor.$patch"
					} else {
						"$major.$minor.$patch-dev.$commits+$sha"
					}
				code to name
			}
		} ?: (1 to "1.0")

android {
	namespace = "dev.yoanndev90.statusbarhider"
	compileSdk = 37

	defaultConfig {
		applicationId = "dev.yoanndev90.statusbarhider"
		minSdk = 26
		targetSdk = 37
		// The release workflow overrides both from the pushed tag (v1.2.3 ->
		// 1002003 / "1.2.3"); local builds derive the same scheme from git
		// (gitVersion above) so every build stays tellable apart and a local
		// release installs over a CI build. A supplied but malformed value
		// fails the build instead of silently shipping versionCode 1.
		versionCode =
			(findProperty("versionCode") as String?)?.let { raw ->
				raw.toIntOrNull()?.takeIf { it > 0 } ?: error("versionCode must be a positive integer, got '$raw'")
			} ?: gitVersion.first
		versionName = (findProperty("versionName") as String?) ?: gitVersion.second
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

detekt {
	// Default ruleset plus config/detekt/detekt.yml on top, so new rules
	// upstream never silently appear as build failures.
	buildUponDefaultConfig = true
	config.setFrom(rootProject.files("config/detekt/detekt.yml"))
	source.setFrom("src/main/kotlin", "src/test/kotlin")
}

dependencies {
	// Compose BOM keeps all androidx.compose versions in sync.
	implementation(platform(libs.compose.bom))
	implementation(libs.activity.compose)
	implementation(libs.activity.ktx)
	// Direct declarations, not transitive conveniences: androidx.core and
	// kotlinx.coroutines are imported across the app, so pinning them here
	// stops an unrelated bump of androidx.activity/lifecycle from silently
	// swapping their versions out from under us.
	implementation(libs.androidx.core.ktx)
	implementation(libs.compose.foundation)
	implementation(libs.compose.material.icons.core)
	implementation(libs.compose.material3)
	implementation(libs.compose.ui)
	implementation(libs.compose.ui.tooling.preview)
	implementation(libs.kotlinx.coroutines.android)
	implementation(libs.kotlinx.serialization.json)
	implementation(libs.lifecycle.runtime.compose)
	implementation(libs.lifecycle.runtime.ktx)
	implementation(libs.lifecycle.viewmodel.compose)
	implementation(libs.lifecycle.viewmodel.ktx)
	implementation(libs.navigation.compose)
	implementation(libs.reorderable)
	debugImplementation(libs.compose.ui.tooling)

	// Shizuku API 13.1.5 + aidl (newProcess is private in the api artifact;
	// we call IShizukuService.newProcess() directly via the aidl artifact).
	// https://github.com/RikkaApps/Shizuku-API
	implementation(libs.api)
	implementation(libs.aidl)
	implementation(libs.provider)

	testImplementation(libs.junit)
}
