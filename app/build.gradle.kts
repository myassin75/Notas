import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.detekt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
}

val rememberApplicationId = "haddad.three.notas"
val rememberCompileSdk = 37
val rememberCompileSdkMinor = 2
val rememberMinSdk = 31
val rememberTargetSdk = 37
val versionCode = 11000
val versionName = "1.10.0"

kotlin {
    jvmToolchain(
        libs.versions.java
            .get()
            .toInt(),
    )
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
        providers
            .gradleProperty("kotlin.compiler.metrics.destination")
            .orNull
            ?.takeIf { it.isNotBlank() }
            ?.let { metricsDestination ->
                freeCompilerArgs.addAll(
                    "-P",
                    "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=$metricsDestination",
                    "-P",
                    "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=$metricsDestination",
                )
            }
    }
}

// Release signing only when keystore.properties exists (CI can write it from secrets).
// Local assembleRelease stays unsigned without that file; sign locally with your own tooling if needed.
val keystoreProps = Properties()
val keystorePropsFile = rootProject.file("keystore.properties")
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}

val releaseStoreFile =
    keystoreProps
        .getProperty("storeFile")
        ?.takeIf { it.isNotBlank() }
        ?.let { rootProject.file(it) }
        ?.takeIf { it.isFile }
val releaseStorePassword = keystoreProps.getProperty("storePassword")?.takeIf { it.isNotBlank() }
val releaseKeyAlias = keystoreProps.getProperty("keyAlias")?.takeIf { it.isNotBlank() }
val releaseKeyPassword = keystoreProps.getProperty("keyPassword")?.takeIf { it.isNotBlank() }

val hasReleaseSigning =
    releaseStoreFile != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null
val previewVersionSuffix =
    providers.gradleProperty("previewVersionSuffix").orNull?.takeIf { it.isNotBlank() }

extensions.configure<ApplicationExtension>("android") {
    namespace = rememberApplicationId
    compileSdk {
        version =
            release(rememberCompileSdk) {
                minorApiLevel = rememberCompileSdkMinor
            }
    }

    defaultConfig.versionCode = versionCode
    defaultConfig.versionName = versionName

    defaultConfig {
        applicationId = rememberApplicationId
        minSdk = rememberMinSdk
        targetSdk = rememberTargetSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "GITHUB_REPO", "\"bikram-agarwal/Remember\"")
        buildConfigField(
            "String",
            "PLAY_STORE_URL",
            "\"https://play.google.com/store/apps/details?id=$rememberApplicationId\"",
        )
        buildConfigField(
            "String",
            "BACKEND_API_BASE_URL",
            "\"https://notas-backend-iota.vercel.app\"",
        )
    }

    androidResources {
        localeFilters += setOf("en")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile!!
                storePassword = releaseStorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildTypes {
        debug {
            val devBackendUrl = project.findProperty("backendApiBaseUrl") as? String
                ?: System.getenv("BACKEND_API_BASE_URL")
                ?: "https://notas-backend-iota.vercel.app"
            buildConfigField("String", "BACKEND_API_BASE_URL", "\"$devBackendUrl\"")
        }
        create("devRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            matchingFallbacks += listOf("release")

            val stagingBackendUrl = project.findProperty("backendApiBaseUrl") as? String
                ?: System.getenv("BACKEND_API_BASE_URL")
                ?: "https://notas-backend-iota.vercel.app"
            buildConfigField("String", "BACKEND_API_BASE_URL", "\"$stagingBackendUrl\"")
        }
        release {
            val prodBackendUrl = project.findProperty("backendApiBaseUrl") as? String
                ?: System.getenv("BACKEND_API_BASE_URL")
                ?: "https://notas-backend-iota.vercel.app"
            buildConfigField("String", "BACKEND_API_BASE_URL", "\"$prodBackendUrl\"")
            versionNameSuffix = previewVersionSuffix
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            applicationIdSuffix = ".gh"
            buildConfigField("Boolean", "CHECK_UPDATES", "true")
            buildConfigField("Boolean", "USE_PLAY_IN_APP_UPDATES", "false")
            buildConfigField("Boolean", "GOOGLE_TASKS_CONNECT_ENABLED", "true")
            buildConfigField("Boolean", "GOOGLE_DRIVE_SYNC_ENABLED", "true")
        }
        create("offline") {
            dimension = "distribution"
            applicationIdSuffix = ".offline"
            buildConfigField("Boolean", "CHECK_UPDATES", "false")
            buildConfigField("Boolean", "USE_PLAY_IN_APP_UPDATES", "false")
            buildConfigField("Boolean", "GOOGLE_TASKS_CONNECT_ENABLED", "false")
            buildConfigField("Boolean", "GOOGLE_DRIVE_SYNC_ENABLED", "false")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("Boolean", "CHECK_UPDATES", "true")
            buildConfigField("Boolean", "USE_PLAY_IN_APP_UPDATES", "true")
            buildConfigField("Boolean", "GOOGLE_TASKS_CONNECT_ENABLED", "true")
            buildConfigField("Boolean", "GOOGLE_DRIVE_SYNC_ENABLED", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        jniLibs {
            keepDebugSymbols += "**/libdatastore_shared_counter.so"
        }
    }

    sourceSets {
        getByName("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
        getByName("github") {
            java.directories.add("src/nonfdroid/java")
            kotlin.directories.add("src/nonfdroid/java")
        }
        getByName("offline") {
            java.directories.add("src/github/java")
            kotlin.directories.add("src/github/java")
            java.directories.add("src/offline/java")
            kotlin.directories.add("src/offline/java")
        }
        getByName("playstore") {
            java.directories.add("src/nonfdroid/java")
            kotlin.directories.add("src/nonfdroid/java")
        }
    }
}

val googleTasksGmsDependencies = file("google-tasks-gms.gradle.kts")
if (googleTasksGmsDependencies.isFile) {
    apply(from = googleTasksGmsDependencies)
}

room {
    schemaDirectory("$projectDir/schemas")
}

val copyHelpDoc =
    tasks.register<Copy>("copyHelpDoc") {
        from(rootProject.file("docs/HELP.md"))
        into("src/main/assets")
    }

tasks.named("preBuild") {
    dependsOn(copyHelpDoc)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}

ktlint {
    android.set(true)
    version.set(libs.versions.ktlint.get())
}

configurations.named("detekt") {
    resolutionStrategy {
        force(
            "io.github.detekt.sarif4k:sarif4k:${libs.versions.sarif4k.get()}",
            "io.github.detekt.sarif4k:sarif4k-jvm:${libs.versions.sarif4k.get()}",
            "io.github.oshai:kotlin-logging:${libs.versions.kotlinLogging.get()}",
        )
    }
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-metadata-jvm") {
            useVersion(libs.versions.kotlin.get())
            because("Hilt 2.59.2 needs a metadata reader that supports Kotlin 2.4.0 metadata.")
        }
    }
}

dependencies {
    implementation(libs.reorderable)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.layout)
    implementation(libs.androidx.material3.adaptive.navigation)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    compileOnly(libs.errorprone.annotations)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.material.kolor)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.documentfile)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.splashscreen)
    add("githubImplementation", platform(libs.firebase.bom))
    add("githubImplementation", libs.firebase.auth)
    add("githubImplementation", libs.google.id)
    add("playstoreImplementation", platform(libs.firebase.bom))
    add("playstoreImplementation", libs.firebase.auth)
    add("playstoreImplementation", libs.google.id)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20240303")
    testImplementation("org.xerial:sqlite-jdbc:3.47.1.0")
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    add("playstoreImplementation", "com.google.android.play:app-update:2.1.0")
    add("playstoreImplementation", "com.google.android.play:app-update-ktx:2.1.0")
    add("playstoreImplementation", "com.google.android.play:review:2.0.2")
    add("playstoreImplementation", "com.google.android.play:review-ktx:2.0.2")
}

val googleServicesJson = file("google-services.json")
if (googleServicesJson.isFile) {
    apply(plugin = "com.google.gms.google-services")
}

abstract class VerifyOfflineFlavorHygieneTask : DefaultTask() {
    @get:InputFiles
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:InputFile
    abstract val manifestFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val forbidden = listOf("com.google.firebase", "com.google.android.gms")
        val matchedFiles = mutableListOf<String>()

        runtimeClasspath.files.forEach { file ->
            val path = file.path
            if (forbidden.any { path.contains(it.replace(".", "/")) || path.contains(it) }) {
                matchedFiles.add(file.name)
            }
        }

        if (matchedFiles.isNotEmpty()) {
            throw GradleException("Found forbidden dependencies in offlineDebugRuntimeClasspath: $matchedFiles")
        }

        val manifest = manifestFile.asFile.get()
        if (!manifest.exists()) {
            throw GradleException("Offline merged manifest not found at ${manifest.absolutePath}")
        }
        val content = manifest.readText()
        if (content.contains("android.permission.INTERNET")) {
            throw GradleException("Found android.permission.INTERNET in offline merged manifest")
        }
        if (content.contains("com.google.firebase")) {
            throw GradleException("Found com.google.firebase in offline merged manifest")
        }
        if (content.contains("com.google.android.gms")) {
            throw GradleException("Found com.google.android.gms in offline merged manifest")
        }
        if (content.contains("firebaseapp.com")) {
            throw GradleException("Found firebaseapp.com deep link in offline merged manifest")
        }
        logger.lifecycle("SUCCESS: Offline flavor is 100% clean of Firebase, Google Play Services, and INTERNET permission.")
    }
}

val verifyOfflineFlavorHygiene = tasks.register<VerifyOfflineFlavorHygieneTask>("verifyOfflineFlavorHygiene") {
    description = "Enforces zero Firebase and zero Google Play Services in the offline flavor"
    group = "verification"
    dependsOn("processOfflineDebugMainManifest")
    runtimeClasspath.from(configurations.named("offlineDebugRuntimeClasspath"))
    manifestFile.set(layout.buildDirectory.file("intermediates/merged_manifest/offlineDebug/processOfflineDebugMainManifest/AndroidManifest.xml"))
}

tasks.matching { it.name == "testOfflineDebugUnitTest" }.configureEach {
    dependsOn(verifyOfflineFlavorHygiene)
}

abstract class VerifyOfflineApkTask : DefaultTask() {
    @get:InputDirectory
    abstract val apkDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val apkDir = apkDirectory.asFile.get()
        val apks = if (apkDir.exists()) apkDir.walkTopDown().filter { it.extension == "apk" }.toList() else emptyList()
        if (apks.isEmpty()) {
            logger.lifecycle("No offline APK found in ${apkDir.path} to verify.")
            return
        }

        val forbidden = listOf("com/google/firebase", "com/google/android/gms")
        for (apk in apks) {
            ZipFile(apk).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith(".dex")) {
                        val bytes = zip.getInputStream(entry).readBytes()
                        val dexText = String(bytes, Charsets.ISO_8859_1)
                        for (pattern in forbidden) {
                            if (dexText.contains(pattern)) {
                                throw GradleException("Forbidden pattern '$pattern' found in ${entry.name} inside ${apk.name}")
                            }
                        }
                    }
                }
            }
            logger.lifecycle("SUCCESS: Verified ${apk.name} has zero Firebase and zero Google Play Services classes.")
        }
    }
}

val verifyOfflineApk = tasks.register<VerifyOfflineApkTask>("verifyOfflineApk") {
    description = "Unzips offline APK and verifies that classes.dex contains no Firebase or Google references"
    group = "verification"
    apkDirectory.set(layout.buildDirectory.dir("outputs/apk/offline"))
}



