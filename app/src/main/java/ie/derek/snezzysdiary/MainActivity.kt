package ie.derek.snezzysdiary

import android.annotation.SuppressLint
import android.Manifest
import android.content.ContentValues
import android.graphics.Color
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Base64
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import org.json.JSONObject

/**
 * Snezzy's Diary
 *
 * The diary screens live in app/src/main/assets/index.html.
 * This activity shows that page full screen in a WebView and gives it
 * three extra powers through the "SnezzyAndroid" bridge:
 *   1. save a backup file into Downloads
 *   2. pick a backup file to restore
 *   3. the tablet back button goes from a diary page back to the calendar
 *   4. pick an Excel or CSV file to turn into a tick-box list
 *   5. set appointment alarms that ring even when the app is closed
 *   6. dark status bars and full screen while scrolling
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    // Opens the system file picker so a backup .json can be chosen
    private val pickBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val text = try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        } ?: return@registerForActivityResult
        // JSONObject.quote safely turns the file text into a JavaScript string
        webView.evaluateJavascript("window.snezzyRestore(${JSONObject.quote(text)})", null)
    }

    // Opens the system file picker for an Excel or CSV file to turn into a list
    private val pickSheetLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@registerForActivityResult
            var name = "list.xlsx"
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0) ?: name
            }
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            webView.evaluateJavascript("window.snezzySheet(${JSONObject.quote(name)}, ${JSONObject.quote(b64)})", null)
        } catch (e: Exception) {
            webView.evaluateJavascript("window.snezzySheet('', '')", null)
        }
    }

    // Asks once for permission to show alarm notifications (Android 13 and newer)
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            runOnUiThread { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Serves the assets folder from a proper https address so saved pages stay put
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true      // needed for saving pages
            settings.allowFileAccess = false
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            overScrollMode = WebView.OVER_SCROLL_NEVER
            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    assetLoader.shouldInterceptRequest(request.url)
            }
            addJavascriptInterface(Bridge(), "SnezzyAndroid")
        }
        setContentView(webView)

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        }

        // Back button: let the page handle it first (diary page -> calendar), otherwise close the app
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("window.snezzyBack ? window.snezzyBack() : false") { handled ->
                    if (handled != "true") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        // Make sure the page you were writing is saved when you leave the app
        webView.evaluateJavascript("window.snezzyFlush && window.snezzyFlush()", null)
    }

    /** Methods the web page can call as SnezzyAndroid.saveBackup(...) and SnezzyAndroid.pickBackup() */
    inner class Bridge {

        @JavascriptInterface
        fun saveBackup(json: String, fileName: String): String {
            return try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return "Android would not create the file."
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                "ok"
            } catch (e: Exception) {
                e.message ?: "Unknown error."
            }
        }

        /** Sets (or moves) an alarm. at is the time in milliseconds, passed as text. */
        @JavascriptInterface
        fun setAlarm(id: String, at: String, title: String, text: String): String {
            return try {
                AlarmReceiver.ensureChannel(this@MainActivity)
                askForNotifications()
                AlarmScheduler.schedule(this@MainActivity, id, at.toDouble().toLong(), title, text)
                "ok"
            } catch (e: Exception) { e.message ?: "error" }
        }

        @JavascriptInterface
        fun cancelAlarm(id: String) { AlarmScheduler.cancel(this@MainActivity, id) }

        @JavascriptInterface
        fun listAlarms(): String = AlarmScheduler.list(this@MainActivity)

        /** Opens the phone's calendar app with the appointment filled in, ready to save. */
        @JavascriptInterface
        fun addToCalendar(title: String, start: String, end: String, notes: String) {
            runOnUiThread {
                try {
                    val i = Intent(Intent.ACTION_INSERT)
                        .setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toDouble().toLong())
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.toDouble().toLong())
                        .putExtra(CalendarContract.Events.TITLE, title)
                        .putExtra(CalendarContract.Events.DESCRIPTION, notes)
                    startActivity(i)
                } catch (e: Exception) { }
            }
        }

        /** Switches the status and navigation bars between the light and dark look. */
        @JavascriptInterface
        fun setDark(dark: Boolean) {
            runOnUiThread {
                val c = if (dark) Color.parseColor("#18171D") else Color.parseColor("#F1EBE0")
                window.statusBarColor = c
                window.navigationBarColor = c
                webView.setBackgroundColor(c)
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }

        /** Hides the status and navigation bars while you scroll down a page. Swipe from the edge to see them. */
        @JavascriptInterface
        fun setFullscreen(on: Boolean) {
            runOnUiThread {
                val ctl = WindowCompat.getInsetsController(window, window.decorView)
                if (on) {
                    ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    ctl.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    ctl.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        @JavascriptInterface
        fun pickSpreadsheet() {
            runOnUiThread {
                pickSheetLauncher.launch(arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel",
                    "application/vnd.oasis.opendocument.spreadsheet",
                    "text/csv",
                    "text/comma-separated-values",
                    "application/octet-stream"
                ))
            }
        }

        @JavascriptInterface
        fun pickBackup() {
            runOnUiThread { pickBackupLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
        }
    }
}
