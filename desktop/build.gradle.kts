// ПК-версия (LWJGL3): тот же core, что в вебе и на Android. Запуск: ./gradlew :desktop:run
// Съёмка экранов для проверки интерфейса: ./gradlew :desktop:shots -> build/shots/*.png
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin { jvmToolchain(17) }

val gdxVersion: String by rootProject.extra

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
}

application { mainClass.set("town.desktop.DesktopLauncherKt") }

tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("assets")
}

tasks.register<JavaExec>("shots") {
    group = "verification"
    description = "Снимки интерфейса в разных размерах окна (build/shots)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("town.desktop.ShotsKt")
    workingDir = rootProject.file("assets")
    args = listOf(rootProject.file("build/shots").absolutePath)
    // -PbigText - снимки с настройкой «Крупный текст»
    if (project.hasProperty("bigText")) systemProperty("shots.big", "1")
}

// Снимки для README: ./gradlew :desktop:readmeShots -> docs/screenshots/*.png
tasks.register<JavaExec>("readmeShots") {
    group = "documentation"
    description = "Снимки для README (docs/screenshots)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("town.desktop.ShotsKt")
    workingDir = rootProject.file("assets")
    args = listOf(rootProject.file("docs/screenshots").absolutePath, "readme")
}

// Город для живой заставки заглавного экрана: ./gradlew :desktop:titleTown -> assets/data/title_town.json
tasks.register<JavaExec>("titleTown") {
    group = "documentation"
    description = "Город для заставки заглавного экрана (assets/data/title_town.json)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("town.desktop.ShotsKt")
    workingDir = rootProject.file("assets")
    args = listOf(rootProject.file("assets/data").absolutePath, "titletown")
}
