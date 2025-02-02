package io.github.dovecoteescapee.byedpi.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.data.Mode
import io.github.dovecoteescapee.byedpi.data.ServiceStatus
import io.github.dovecoteescapee.byedpi.services.ByeDpiProxyService
import io.github.dovecoteescapee.byedpi.services.ServiceManager
import io.github.dovecoteescapee.byedpi.utility.GoogleVideoUtils
import io.github.dovecoteescapee.byedpi.utility.HistoryUtils
import io.github.dovecoteescapee.byedpi.utility.getPreferences
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

class TestActivity : AppCompatActivity() {

    private lateinit var scrollTextView: ScrollView
    private lateinit var progressTextView: TextView
    private lateinit var resultsTextView: TextView
    private lateinit var startStopButton: Button

    private lateinit var cmdHistoryUtils: HistoryUtils
    private lateinit var sites: MutableList<String>
    private lateinit var cmds: List<String>

    private var originalCmdArgs: String = ""
    private var testJob: Job? = null

    private val proxyIp: String = "127.0.0.1"
    private val proxyPort: Int = 10080

    private var isTesting: Boolean
        get() = prefs.getBoolean("is_test_running", false)
        set(value) {
            prefs.edit().putBoolean("is_test_running", value).apply()
        }

    private val prefs by lazy { getPreferences() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_proxy_test)

        cmdHistoryUtils = HistoryUtils(this)
        scrollTextView = findViewById(R.id.scrollView)
        startStopButton = findViewById(R.id.startStopButton)
        resultsTextView = findViewById(R.id.resultsTextView)
        progressTextView = findViewById(R.id.progressTextView)

        resultsTextView.movementMethod = LinkMovementMethod.getInstance()

        if (isTesting) {
            progressTextView.text = getString(R.string.test_proxy_error)
            resultsTextView.text = getString(R.string.test_crash)
        } else {
            lifecycleScope.launch {
                val previousLogs = loadLog()
                if (previousLogs.isNotEmpty()) {
                    progressTextView.text = getString(R.string.test_complete)
                    resultsTextView.text = ""
                    displayLog(previousLogs)
                }
            }
        }

        startStopButton.setOnClickListener {
            if (isTesting) {
                stopTesting()
            } else {
                startTesting()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTesting) {
                    stopTesting()
                }
                finish()
            }
        })

        requestedOrientation = when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            else -> {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_test, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                if (!isTesting) {
                    val intent = Intent(this, TestSettingsActivity::class.java)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
                }
                true
            }
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startProxyService() {
        try {
            ServiceManager.start(this, Mode.Proxy)
        } catch (e: Exception) {
            Log.e("TestActivity", "Error start proxy service: ${e.message}")
        }
    }

    private fun stopProxyService() {
        try {
            ServiceManager.stop(this)
        } catch (e: Exception) {
            Log.e("TestActivity", "Error stop proxy service: ${e.message}")
        }
    }

    private suspend fun waitForProxyStatus(
        statusNeeded: ServiceStatus,
        timeoutMillis: Long = 5000L
    ): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (isProxyRunning() == (statusNeeded == ServiceStatus.Connected)) {
                return true
            }
            delay(100)
        }
        return false
    }

    private suspend fun isProxyRunning(): Boolean = withContext(Dispatchers.IO) {
        ByeDpiProxyService.getStatus() == ServiceStatus.Connected
    }

    private fun startTesting() {
        isTesting = true

        startStopButton.text = getString(R.string.test_stop)
        resultsTextView.text = ""
        progressTextView.text = ""

        originalCmdArgs = prefs.getString("byedpi_cmd_args", "").orEmpty()

        sites = loadSites().toMutableList()
        cmds = loadCmds()

        clearLog()

        testJob = lifecycleScope.launch {
            val delaySec = prefs.getString("byedpi_proxytest_delay", "1")?.toIntOrNull() ?: 1
            val useGeneratedGoogleDomain = prefs.getBoolean("byedpi_proxytest_gdomain", true)
            val fullLog = prefs.getBoolean("byedpi_proxytest_fulllog", false)
            val logClickable = prefs.getBoolean("byedpi_proxytest_logclickable", false)
            val requestsCount =
                prefs.getString("byedpi_proxytest_requestsсount", "1")
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
                    ?: 1

            if (useGeneratedGoogleDomain) {
                val googleVideoDomain = GoogleVideoUtils().generateGoogleVideoDomain()
                if (googleVideoDomain != null) {
                    sites.add(googleVideoDomain)
                    appendTextToResults("--- $googleVideoDomain ---\n\n")
                    Log.i("TestActivity", "Added auto-generated Google domain: $googleVideoDomain")
                } else {
                    Log.e("TestActivity", "Failed to generate Google domain")
                }
            }

            val successfulCmds = mutableListOf<Pair<String, Int>>()

            for ((index, cmd) in cmds.withIndex()) {
                val cmdIndex = index + 1
                progressTextView.text = getString(R.string.test_process, cmdIndex, cmds.size)

                val testCmd = "--ip $proxyIp --port $proxyPort $cmd"
                updateCmdInPreferences(testCmd)

                if (!isProxyRunning()) startProxyService()
                waitForProxyStatus(ServiceStatus.Connected)

                if (logClickable) {
                    appendLinkToResults("$cmd\n")
                } else {
                    appendTextToResults("$cmd\n")
                }

                delay(delaySec * 1000L)
                val totalRequests = sites.size * requestsCount
                val checkResults = checkSitesAsync(sites, requestsCount, fullLog)
                val successfulCount = checkResults.sumOf { it.second }
                val successPercentage = (successfulCount * 100) / totalRequests
                delay(delaySec * 1000L)

                if (successPercentage >= 50) successfulCmds.add(cmd to successPercentage)
                appendTextToResults("$successfulCount/$totalRequests ($successPercentage%)\n\n")

                if (isProxyRunning()) stopProxyService()
                waitForProxyStatus(ServiceStatus.Disconnected)
            }

            successfulCmds.sortByDescending { it.second }

            progressTextView.text = getString(R.string.test_complete)
            appendTextToResults("${getString(R.string.test_good_cmds)}\n\n")

            successfulCmds.forEachIndexed { index, (cmd, success) ->
                appendTextToResults("${index + 1}. ")
                appendLinkToResults("$cmd\n")
                appendTextToResults("$success%\n\n")
            }

            appendTextToResults(getString(R.string.test_complete_info))
            stopTesting()
        }
    }

    private fun stopTesting() {
        updateCmdInPreferences(originalCmdArgs)

        isTesting = false
        testJob?.cancel()
        startStopButton.text = getString(R.string.test_start)

        lifecycleScope.launch {
            if (isProxyRunning()) {
                stopProxyService()
            }
        }
    }

    private fun appendTextToResults(text: String) {
        resultsTextView.append(text)
        if (isTesting) saveLog(text)
        scrollToBottom()
    }

    private fun appendLinkToResults(text: String) {
        val spannableString = SpannableString(text)
        val menuItems = arrayOf(
            getString(R.string.cmd_history_apply),
            getString(R.string.cmd_history_copy)
        )

        spannableString.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    AlertDialog.Builder(this@TestActivity)
                        .setTitle(getString(R.string.cmd_history_menu))
                        .setItems(menuItems) { _, which ->
                            when (which) {
                                0 -> addToHistory(text.trim())
                                1 -> copyToClipboard(text.trim())
                            }
                        }
                        .show()
                }
            },
            0,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        resultsTextView.append(spannableString)
        if (isTesting) saveLog("{$text}")
        scrollToBottom()
    }

    private fun scrollToBottom() {
        scrollTextView.post {
            scrollTextView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("command", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun addToHistory(command: String) {
        updateCmdInPreferences(command)
        cmdHistoryUtils.addCommand(command)
    }

    private fun displayLog(log: String) {
        log.split("{", "}").forEachIndexed { index, part ->
            if (index % 2 == 0) {
                appendTextToResults(part)
            } else {
                appendLinkToResults(part)
            }
        }
    }

    private fun saveLog(text: String) {
        val file = File(filesDir, "proxy_test.log")
        file.appendText(text)
    }

    private fun loadLog(): String {
        val file = File(filesDir, "proxy_test.log")
        return if (file.exists()) file.readText() else ""
    }

    private fun clearLog() {
        val file = File(filesDir, "proxy_test.log")
        file.writeText("")
    }

    private fun loadSites(): List<String> {
        val userDomains = prefs.getBoolean("byedpi_proxytest_userdomains", false)
        return if (userDomains) {
            val domains = prefs.getString("byedpi_proxytest_domains", "").orEmpty()
            domains.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } else {
            assets.open("proxytest_sites.txt").bufferedReader().useLines { it.toList() }
        }
    }

    private fun loadCmds(): List<String> {
        val userCommands = prefs.getBoolean("byedpi_proxytest_usercommands", false)
        return if (userCommands) {
            val commands = prefs.getString("byedpi_proxytest_commands", "").orEmpty()
            commands.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } else {
            assets.open("proxytest_cmds.txt").bufferedReader().useLines { it.toList() }
        }
    }

    private suspend fun checkSitesAsync(
        sites: List<String>,
        requestsCount: Int,
        fullLog: Boolean
    ): List<Pair<String, Int>> {
        return withContext(Dispatchers.IO) {
            sites.map { site ->
                async {
                    if (!isProxyRunning()) return@async site to 0

                    val successCount = checkSiteAccess(site, requestsCount)
                    if (fullLog) {
                        withContext(Dispatchers.Main) {
                            appendTextToResults("$site - $successCount/$requestsCount\n")
                        }
                    }
                    site to successCount
                }
            }.awaitAll()
        }
    }

    private suspend fun checkSiteAccess(
        site: String,
        requestsCount: Int
    ): Int = withContext(Dispatchers.IO) {
        var responseCount = 0
        val formattedUrl = if (site.startsWith("http://") || site.startsWith("https://"))
            site
        else
            "https://$site"

        repeat(requestsCount) { attempt ->
            Log.i("CheckSite", "Attempt ${attempt + 1}/$requestsCount for $site")
            try {
                val url = URL(formattedUrl)
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyIp, proxyPort))
                val connection = url.openConnection(proxy) as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 2000
                connection.readTimeout = 2000

                val responseCode = connection.responseCode
                responseCount++
                Log.i("CheckSite", "Response for $site: $responseCode")
                connection.disconnect()
            } catch (e: Exception) {
                Log.e("CheckSite", "Error accessing $site: ${e.message}")
            }
        }
        responseCount
    }

    private fun updateCmdInPreferences(cmd: String) {
        prefs.edit().putString("byedpi_cmd_args", cmd).apply()
    }
}
