// Веб-версия: тот же core, собранный в JavaScript через TeaVM (gdx-teavm)
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.github.xpenatan.gdx-teavm")
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core"))
    implementation("com.github.xpenatan.gdx-teavm:gdx-freetype-web:1.6.2")
}

gdxTeaVM {
    assets.from(rootProject.file("assets"))

    webDefaults {
        mainClass = "town.web.WebLauncher"
        htmlTitle = "Hearthvale"
    }

    js {}
}

// После сборки: свой логотип загрузки, значок вкладки, тёмно-зелёный фон страницы, как у игры;
// viewport - сразу в HTML: встроенные браузеры (iOS, приложения) иначе создают холст по ширине 980 и показывают его кусок.
// Ресурсы и скрипты - в папке версии v<версия>/ (WebLauncher берёт их оттуда), app.js - с меткой версии:
// после выпуска все адреса новые, и браузер не смешивает старые файлы из кэша (Pages кэширует на 10 минут) с новыми.
tasks.matching { it.name == "gdx_teavm_web_js_build" }.configureEach {
    doLast {
        val version = Regex("const val VERSION = \"([^\"]+)\"").find(rootProject.file("core/src/main/kotlin/town/Hearthvale.kt").readText())!!.groupValues[1]
        val app = layout.buildDirectory.dir("dist/js/webapp").get().asFile
        rootProject.file("assets/web/logo.png").copyTo(File(app, "assets/startup-logo.png"), overwrite = true)
        val vdir = File(app, "v$version")
        vdir.deleteRecursively()
        app.listFiles { f -> f.isDirectory && f.name.startsWith("v") && f.name != "v$version" }?.forEach { it.deleteRecursively() }
        vdir.mkdirs()
        for (name in listOf("assets", "scripts")) File(app, name).renameTo(File(vdir, name))
        val index = File(app, "index.html")
        var html = index.readText()
        val head = listOf(
            "<link rel=\"icon\" type=\"image/png\" href=\"v$version/assets/web/favicon.png\">",
            "<link rel=\"apple-touch-icon\" href=\"v$version/assets/web/icon-180.png\">",
            "<link rel=\"manifest\" href=\"manifest.webmanifest\">",
            "<meta name=\"theme-color\" content=\"#1f2a1b\">",
            // iPhone: «На экран Домой» - приложение на весь экран, без адресной строки
            "<meta name=\"apple-mobile-web-app-capable\" content=\"yes\">",
            "<meta name=\"mobile-web-app-capable\" content=\"yes\">",
            // строка состояния - непрозрачная: игра начинается под часами, а высота окна верная
            // (с black-translucent iOS рисует страницу под строкой, но сообщает высоту без неё - снизу пустая полоса)
            "<meta name=\"apple-mobile-web-app-status-bar-style\" content=\"black\">",
            "<meta name=\"apple-mobile-web-app-title\" content=\"Hearthvale\">",
            // заставка при запуске с экрана «Домой» (иначе белый экран): картинка по размеру экрана iPhone (tools Icon.SPLASH)
            *listOf(750 to 1334, 828 to 1792, 1080 to 2340, 1125 to 2436, 1170 to 2532, 1179 to 2556,
                1206 to 2622, 1242 to 2688, 1284 to 2778, 1290 to 2796, 1320 to 2868).map { (w, h) ->
                val d = if (w <= 828) 2 else 3
                "<link rel=\"apple-touch-startup-image\" media=\"(device-width: ${w / d}px) and (device-height: ${h / d}px) and " +
                    "(-webkit-device-pixel-ratio: $d) and (orientation: portrait)\" href=\"v$version/assets/web/splash/splash_${w}x$h.jpg\">"
            }.toTypedArray(),
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover\">",
            "<title>",
        ).joinToString("\n        ")
        // офлайн: сервис-воркер кладёт в кэш все файлы версии; хранилище - постоянное (iOS не стирает сохранения)
        val register = "<script>if('serviceWorker' in navigator){navigator.serviceWorker.register('sw.js').catch(function(){});}" +
            "if(navigator.storage&&navigator.storage.persist){navigator.storage.persist().catch(function(){});}</script>\n</body>"
        html = html.replace("<title>", head).replace("background: #000;", "background: #1f2a1b;")
            .replace("src=\"app.js\"", "src=\"app.js?v=$version\"")
            .replace("\"assets/", "\"v$version/assets/")
            .replace("</body>", register)
        index.writeText(html)

        // описание приложения (установка на экран «Домой», полноэкранный запуск)
        File(app, "manifest.webmanifest").writeText("""
            {
              "name": "Hearthvale",
              "short_name": "Hearthvale",
              "start_url": "./",
              "scope": "./",
              "display": "fullscreen",
              "orientation": "any",
              "background_color": "#1f2a1b",
              "theme_color": "#1f2a1b",
              "icons": [
                { "src": "v$version/assets/web/icon-192.png", "sizes": "192x192", "type": "image/png" },
                { "src": "v$version/assets/web/icon-512.png", "sizes": "512x512", "type": "image/png" }
              ]
            }
        """.trimIndent())

        // сервис-воркер: при установке - все файлы этой версии в кэш (игра дальше работает без сети);
        // страница - из сети, нет сети - из кэша; файлы версии - из кэша (их адреса меняются с версией);
        // старые кэши удаляются, когда новая версия установлена
        val files = app.walkTopDown().filter { it.isFile && !it.path.contains("WEB-INF") && it.name != "sw.js" }
            .map { it.relativeTo(app).invariantSeparatorsPath }
            .map { if (it == "app.js") "app.js?v=$version" else it }
            .sorted().toList()
        val list = (listOf("./") + files).joinToString(",\n  ") { "\"$it\"" }
        File(app, "sw.js").writeText("""
            // Hearthvale $version: офлайн-кэш (создаётся сборкой web/build.gradle.kts)
            const CACHE = 'hearthvale-$version';
            const FILES = [
              $list
            ];
            self.addEventListener('install', e => {
              e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
            });
            self.addEventListener('activate', e => {
              e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k.startsWith('hearthvale-') && k !== CACHE).map(k => caches.delete(k))))
                .then(() => self.clients.claim()));
            });
            self.addEventListener('fetch', e => {
              const req = e.request;
              if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return;
              if (req.mode === 'navigate') {
                // страница: свежая из сети (узнать о новой версии), без сети - из кэша
                e.respondWith(fetch(req).then(r => { const copy = r.clone(); caches.open(CACHE).then(c => c.put('./', copy)); return r; })
                  .catch(() => caches.match('./').then(r => r || caches.match('index.html'))));
                return;
              }
              e.respondWith(caches.match(req).then(r => r || fetch(req).then(resp => {
                if (resp.ok) { const copy = resp.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
                return resp;
              })));
            });
        """.trimIndent())
    }
}
