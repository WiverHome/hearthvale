import java.util.Properties

// Нативная Android-версия: тот же core на libGDX, без WebView. Kotlin встроен в AGP 9.
plugins {
    id("com.android.application")
}

val gdxVersion: String by rootProject.extra
val natives: Configuration by configurations.creating

android {
    namespace = "town.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.wiverhome.hearthvale"
        minSdk = 26
        targetSdk = 36
        versionCode = 28
        versionName = "1.0.0"
    }

    // Подпись релиза: ключ release.jks и пароли в keystore.properties в корне (оба не в git, хранить копию!)
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        val props = Properties().apply { keystoreFile.inputStream().use { load(it) } }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
        buildTypes["release"].signingConfig = signingConfigs["release"]
    }

    sourceSets["main"].apply {
        assets.srcDirs("../assets")
        jniLibs.srcDirs("libs")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "META-INF/*" }
    // общая папка assets/ - и для веба: иконки и заставки веба (web/) и учёт ресурсов в APK не нужны
    androidResources { ignoreAssetsPattern = "!web:!CREDITS.md:!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~" }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
    for (abi in listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) {
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$abi")
        natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-$abi")
    }
}

// Нативные .so libGDX раскладываются по ABI в android/libs перед сборкой
val copyAndroidNatives by tasks.registering {
    doFirst {
        natives.files.forEach { jar ->
            val abi = jar.nameWithoutExtension.substringAfterLast("natives-")
            val outputDir = file("libs/$abi")
            outputDir.mkdirs()
            copy {
                from(zipTree(jar))
                into(outputDir)
                include("*.so")
            }
        }
    }
}
tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn(copyAndroidNatives)
}
