package town.web

import com.github.xpenatan.gdx.teavm.backends.web.WebApplication
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration
import com.github.xpenatan.gdx.teavm.backends.web.WebAssetPreloadListener
import org.teavm.jso.JSBody
import town.Hearthvale

object WebLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        lockPage()
        val config = WebApplicationConfiguration()
        config.width = 0
        config.height = 0
        config.preloadListener = WebAssetPreloadListener { it.loadScript("freetype.js") }
        // ресурсы и скрипты - в папке версии (web/build.gradle.kts): после выпуска браузер не берёт старые из кэша
        config.baseUrlProvider = com.github.xpenatan.gdx.teavm.backends.web.utils.WebBaseUrlProvider {
            com.github.xpenatan.gdx.teavm.backends.web.utils.WebDefaultBaseUrlProvider().baseUrl + "v" + Hearthvale.VERSION + "/"
        }
        val game = Hearthvale(browserLanguage())
        game.touchScreen = coarsePointer()
        game.clipboardCodes = false // вставка из буфера в gdx-teavm работает только внутри самой игры
        game.webInsets = { safeInsets() }
        game.saveFiles = object : Hearthvale.SaveFiles {
            override fun export(name: String, text: String) = exportFile(name, text)
            override fun exportImage(name: String, w: Int, h: Int, rgba: ByteArray) =
                exportPng(name, w, h, String(com.badlogic.gdx.utils.Base64Coder.encode(rgba)))
            override fun import(done: (String?) -> Unit) = importFile(TextCallback { t -> com.badlogic.gdx.Gdx.app.postRunnable { done(t) } })
        }
        WebApplication(game, config)
        // вкладку прячут или закрывают - сохраняем сразу (автосохранение раз в полминуты могло не успеть)
        onPageHide { game.saveNow() }
    }

    @org.teavm.jso.JSFunctor
    fun interface Callback : org.teavm.jso.JSObject {
        fun run()
    }

    @org.teavm.jso.JSFunctor
    fun interface TextCallback : org.teavm.jso.JSObject {
        fun run(text: String?)
    }

    /**
     * Сохранение в файл: на iPhone и Android - через «Поделиться» (там «Сохранить в Файлы»), иначе - обычная загрузка
     */
    @JSBody(params = ["name", "text"], script = """
        var blob = new Blob([text], { type: 'application/json' });
        try {
            var file = new File([blob], name, { type: 'application/json' });
            // «Поделиться» - только на сенсорных (телефон), на ПК - обычная загрузка
                var touch = window.matchMedia && window.matchMedia('(pointer: coarse)').matches;
                if (touch && navigator.canShare && navigator.canShare({ files: [file] })) { navigator.share({ files: [file], title: name }).catch(function() {}); return; }
        } catch (e) {}
        var a = document.createElement('a');
        a.href = URL.createObjectURL(blob); a.download = name;
        document.body.appendChild(a); a.click();
        setTimeout(function() { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
    """)
    @JvmStatic
    external fun exportFile(name: String, text: String)

    /** Снимок: RGBA (base64) -> холст -> PNG; на телефоне через «Поделиться», иначе обычная загрузка */
    @JSBody(params = ["name", "w", "h", "b64"], script = """
        var W = Number(w) | 0, H = Number(h) | 0;
        var bin = atob(b64), n = bin.length, data = new Uint8ClampedArray(n);
        for (var i = 0; i < n; i++) data[i] = bin.charCodeAt(i);
        var c = document.createElement('canvas'); c.width = W; c.height = H;
        // не new ImageData: на странице игры это имя занято - картинку даёт сам холст
        var ctx = c.getContext('2d'), img = ctx.createImageData(W, H);
        img.data.set(data);
        ctx.putImageData(img, 0, 0);
        c.toBlob(function(blob) {
            if (!blob) return;
            function download() {
                var a = document.createElement('a');
                a.href = URL.createObjectURL(blob); a.download = name;
                document.body.appendChild(a); a.click();
                setTimeout(function() { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
            }
            // «Поделиться» - только на сенсорных (телефон: сохранить в фото); на ПК и если не вышло - обычная загрузка
            try {
                var touch = window.matchMedia && window.matchMedia('(pointer: coarse)').matches;
                var file = new File([blob], name, { type: 'image/png' });
                if (touch && navigator.canShare && navigator.canShare({ files: [file] })) {
                    navigator.share({ files: [file], title: name }).catch(function(e) { if (!e || e.name !== 'AbortError') download(); });
                    return;
                }
            } catch (e) {}
            download();
        }, 'image/png');
    """)
    @JvmStatic
    external fun exportPng(name: String, w: Int, h: Int, b64: String)

    /** Выбрать файл сохранения; f(текст), отмена - ничего */
    @JSBody(params = ["f"], script = """
        var input = document.createElement('input');
        input.type = 'file'; input.accept = '.json,application/json,text/plain';
        input.style.display = 'none';
        input.onchange = function() {
            var file = input.files && input.files[0];
            input.remove();
            if (!file) return;
            var r = new FileReader();
            r.onload = function() { f(String(r.result)); };
            r.onerror = function() { f(null); };
            r.readAsText(file);
        };
        document.body.appendChild(input); input.click();
    """)
    @JvmStatic
    external fun importFile(f: TextCallback)

    @JSBody(params = ["f"], script = """
        document.addEventListener('visibilitychange', function() { if (document.visibilityState === 'hidden') f(); });
        window.addEventListener('pagehide', function() { f(); });
    """)
    @JvmStatic
    external fun onPageHide(f: Callback)

    /**
     * Страница статична: холст на всё окно, без прокрутки, без масштаба и жестов браузера
     * (масштаб и перемещение карты делает сама игра). Размер окна пересчитывается ещё несколько раз после
     * загрузки и при повороте: встроенные браузеры сообщают настоящую ширину не сразу.
     */
    @JSBody(script = """
        var css = 'html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;position:fixed;inset:0;' +
            'touch-action:none;overscroll-behavior:none;-webkit-user-select:none;user-select:none;background:#1a1a1f;' +
            '-webkit-touch-callout:none;-webkit-tap-highlight-color:transparent}' +
            'body>div{width:100%;height:100%;overflow:hidden}canvas{display:block;touch-action:none;outline:none}';
        var st = document.createElement('style'); st.textContent = css; document.head.appendChild(st);
        var m = document.querySelector('meta[name=viewport]');
        if (!m) { m = document.createElement('meta'); m.name = 'viewport'; document.head.appendChild(m); }
        m.content = 'width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover';
        document.addEventListener('contextmenu', function(e) { e.preventDefault(); });
        document.addEventListener('gesturestart', function(e) { e.preventDefault(); });
        document.addEventListener('wheel', function(e) { if (e.ctrlKey) e.preventDefault(); }, { passive: false });
        var kick = function() { window.dispatchEvent(new Event('resize')); };
        [100, 400, 1000, 2500].forEach(function(t) { setTimeout(kick, t); });
        window.addEventListener('orientationchange', function() { setTimeout(kick, 300); });
        if (window.visualViewport) window.visualViewport.addEventListener('resize', function() { setTimeout(kick, 50); });
        window.addEventListener('pageshow', function() { setTimeout(kick, 100); });
        // iOS приостанавливает звук при сворачивании - при возвращении и первом касании возобновить
        var resume = function() { try { if (window.Howler && Howler.ctx && Howler.ctx.state !== 'running') Howler.ctx.resume(); } catch (e) {} };
        document.addEventListener('visibilitychange', function() { if (document.visibilityState === 'visible') resume(); });
        document.addEventListener('touchend', resume, { passive: true });
        window.addEventListener('pageshow', resume);
        // долгое нажатие - без системного меню и лупы
        document.addEventListener('contextmenu', function(e) { e.preventDefault(); });
    """)
    @JvmStatic
    external fun lockPage()

    /** Безопасные отступы (вырез, полоса «Домой») из CSS env(): «верх,лево,право,низ,ширина окна» в точках CSS */
    @JSBody(script = """
        var d = document.getElementById('safe-probe');
        if (!d) {
            d = document.createElement('div'); d.id = 'safe-probe';
            d.style.cssText = 'position:fixed;left:0;top:0;width:0;height:0;visibility:hidden;pointer-events:none;' +
                'padding-top:env(safe-area-inset-top,0px);padding-left:env(safe-area-inset-left,0px);' +
                'padding-right:env(safe-area-inset-right,0px);padding-bottom:env(safe-area-inset-bottom,0px)';
            document.body.appendChild(d);
        }
        var s = getComputedStyle(d);
        var w = document.documentElement.clientWidth || window.innerWidth;
        return [parseFloat(s.paddingTop) || 0, parseFloat(s.paddingLeft) || 0, parseFloat(s.paddingRight) || 0, parseFloat(s.paddingBottom) || 0, w].join(',');
    """)
    @JvmStatic
    external fun safeInsets(): String

    /** Основное устройство ввода - палец (телефон, планшет), а не мышь */
    @JSBody(script = "return !!(window.matchMedia && window.matchMedia('(pointer: coarse)').matches);")
    @JvmStatic
    external fun coarsePointer(): Boolean

    @JSBody(script = "return navigator.language || 'en';")
    @JvmStatic
    external fun browserLanguage(): String
}
