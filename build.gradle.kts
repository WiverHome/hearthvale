plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.10" apply false
    id("com.github.xpenatan.gdx-teavm") version "1.6.2" apply false
    id("com.android.application") version "9.4.1" apply false
}

// Версии зависимостей в одном месте
extra["gdxVersion"] = "1.14.2"
