// Вся игра: симуляция (town.sim, без графики) + отрисовка и ввод. Общий для веба и Android.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin { jvmToolchain(17) }

dependencies {
    api("com.badlogicgames.gdx:gdx:${rootProject.extra["gdxVersion"]}")
    api("com.badlogicgames.gdx:gdx-freetype:${rootProject.extra["gdxVersion"]}")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { showStandardStreams = true }
    exclude("**/BalanceTest*")
}

// Баланс: бот играет песочницу, отчёт в core/build/balance/*.txt
// ./gradlew :core:balance [-Pdays=16 - сутки, по умолчанию 40 минут игры] [-Pseeds=42,7,2026] [-Psalts=4 - сдвиги случайности]
tasks.register<Test>("balance") {
    group = "verification"
    description = "Бот играет песочницу и пишет отчёт по суткам (core/build/balance)"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    testLogging { showStandardStreams = true }
    filter { includeTestsMatching("town.sim.BalanceTest") }
    outputs.upToDateWhen { false }
    // карты и сценарии играются параллельно (BalanceTest), а три теста класса - одновременно
    maxHeapSize = "4g"
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
    project.findProperty("days")?.let { systemProperty("balance.days", it) }
    project.findProperty("salts")?.let { systemProperty("balance.salts", it) }
    systemProperty("balance.seeds", project.findProperty("seeds") ?: "42,7,2026,1,99,555")
}
