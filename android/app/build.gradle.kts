import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.roborazzi)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/** Vrednost iz -P, local.properties ili okruženja (ključevi nikad nisu u repozitorijumu). */
fun config(name: String, default: String = ""): String =
    (project.findProperty(name) as String?) ?: localProperties.getProperty(name) ?: System.getenv(name) ?: default

/** Projekat iz google-services.json, za domen deljenih linkova (<projekat>.web.app). */
val firebaseProjectId: String = Regex("\"project_id\"\\s*:\\s*\"([^\"]+)\"")
    .find(file("google-services.json").readText())?.groupValues?.get(1) ?: "demo-termin"

android {
    namespace = "rs.termin.app"
    // Trenutne AndroidX biblioteke traže compileSdk 37; targetSdk prati zahtev Google Play-a (36).
    compileSdk = 37

    defaultConfig {
        applicationId = "rs.termin.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["mapsApiKey"] = config("MAPS_API_KEY")
        manifestPlaceholders["appLinkHost"] = "$firebaseProjectId.web.app"
        buildConfigField("boolean", "HAS_MAPS_KEY", config("MAPS_API_KEY").isNotBlank().toString())
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${config("GOOGLE_WEB_CLIENT_ID")}\"")
        buildConfigField("String", "APP_LINK_HOST", "\"$firebaseProjectId.web.app\"")
    }

    signingConfigs {
        create("release") {
            val storePath = config("TERMIN_KEYSTORE")
            if (storePath.isNotBlank()) {
                storeFile = file(storePath)
                storePassword = config("TERMIN_KEYSTORE_PASSWORD")
                keyAlias = config("TERMIN_KEY_ALIAS")
                keyPassword = config("TERMIN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "USE_EMULATORS", "true")
            buildConfigField("String", "EMULATOR_HOST", "\"${config("termin.emulatorHost", "10.0.2.2")}\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "USE_EMULATORS", "false")
            buildConfigField("String", "EMULATOR_HOST", "\"\"")
            // Bez produkcijskog ključa release se potpisuje debug ključem (samo za lokalno merenje).
            signingConfig = if (config("TERMIN_KEYSTORE").isNotBlank()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                // Mapiranje se šalje samo kad je podešen pravi Firebase projekat.
                mappingFileUploadEnabled = config("TERMIN_CRASHLYTICS_UPLOAD") == "true"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        localeFilters += listOf("b+sr+Latn", "en")
        generateLocaleConfig = true
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "/META-INF/NOTICE*", "DebugProbesKt.bin")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "4g"
                // Robolectric za Android 16 pristupa internim JDK klasama (FileDescriptor).
                it.jvmArgs(
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                )
            }
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
        lintConfig = file("lint.xml")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors = true
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(project.layout.projectDirectory.file("compose-stability.conf"))
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.installations)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.geofire.common)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.maps.compose)
    implementation(libs.play.services.location)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.leakcanary)

    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.navigation.testing)
}
