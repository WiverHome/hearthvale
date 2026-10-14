// Генератор ресурсов: рисует спрайты (Java2D) и собирает атлас, синтезирует звуки и музыку.
// Запуск: ./gradlew :tools:run - пишет assets/sprites/atlas.png + atlas.json и assets/sounds/* (WAV, музыка MP3)
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core"))
    implementation("de.sciss:jump3r:1.0.5")
}

application { mainClass.set("town.tools.AssetsKt") }

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    systemProperty("java.awt.headless", "true")
}
