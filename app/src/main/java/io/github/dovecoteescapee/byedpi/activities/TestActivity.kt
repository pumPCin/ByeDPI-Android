package io.github.dovecoteescapee.byedpi.activities

import android.annotation.SuppressLint
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
import io.github.dovecoteescapee.byedpi.utility.getPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

class TestActivity : AppCompatActivity() {
    private lateinit var sites: List<String>
    private lateinit var cmds: List<String>

    private lateinit var scrollTextView: ScrollView
    private lateinit var progressTextView: TextView
    private lateinit var resultsTextView: TextView
    private lateinit var startStopButton: Button

    private var isTesting = false
    private var originalCmdArgs: String = ""
    private var testJob: Job? = null
    private var proxyIp: String = "127.0.0.1"
    private var proxyPort: Int = 1080

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_proxy_test)

        scrollTextView = findViewById(R.id.scrollView)
        startStopButton = findViewById(R.id.startStopButton)
        resultsTextView = findViewById(R.id.resultsTextView)
        progressTextView = findViewById(R.id.progressTextView)

        resultsTextView.movementMethod = LinkMovementMethod.getInstance()

        lifecycleScope.launch {
            val previousLogs = loadLog()

            if (previousLogs.isNotEmpty()) {
                progressTextView.text = getString(R.string.test_complete)
                resultsTextView.text = ""
                displayLog(previousLogs)
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

        @SuppressLint("SwitchIntDef", "SourceLockedOrientationActivity")
        when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Configuration.ORIENTATION_PORTRAIT -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
                    Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT)
                        .show()
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

    private suspend fun waitForProxyToStart(timeout: Long = 5000): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeout) {
            if (isProxyRunning()) {
                Log.i("TestDPI", "Wait done: Proxy connected")
                return true
            }
            delay(100)
        }
        return false
    }

    private suspend fun waitForProxyToStop(timeout: Long = 5000): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeout) {
            if (!isProxyRunning()) {
                Log.i("TestDPI", "Wait done: Proxy disconnected")
                return true
            }
            delay(100)
        }
        return false
    }

    private suspend fun isProxyRunning(): Boolean {
        return withContext(Dispatchers.IO) {
            ByeDpiProxyService.getStatus() == ServiceStatus.Connected
        }
    }

    private fun startTesting() {
        isTesting = true
        startStopButton.text = getString(R.string.test_stop)
        resultsTextView.text = ""
        progressTextView.text = ""

        originalCmdArgs = getPreferences().getString("byedpi_cmd_args", "").toString()
        sites = loadSites().toMutableList()
        cmds = loadCmds()
        clearLog()

        val successfulCmds = mutableListOf<Pair<String, Int>>()
        val delay = getPreferences().getString("byedpi_proxytest_delay", "1")?.toIntOrNull() ?: 1
        val requestsCount = getPreferences().getString("byedpi_proxytest_requestsсount", "1")?.toIntOrNull() ?: 1
        val gdomain = getPreferences().getBoolean("byedpi_proxytest_gdomain", true)
        val fullLog = getPreferences().getBoolean("byedpi_proxytest_fulllog", false)
        val logClickable = getPreferences().getBoolean("byedpi_proxytest_logclickable", false)
        var cmdIndex = 0

        testJob = lifecycleScope.launch {
            if (gdomain) {
                val googleVideoDomain = GoogleVideoUtils().generateGoogleVideoDomain()
                if (googleVideoDomain != null) {
                    (sites as MutableList<String>).add(googleVideoDomain)
                    appendTextToResults("--- $googleVideoDomain ---\n\n")
                    Log.i("TestActivity", "Added auto-generated Google domain: $googleVideoDomain")
                } else {
                    Log.e("TestActivity", "Failed to generate Google domain")
                }
            }

            for (cmd in cmds) {
                cmdIndex++
                progressTextView.text = getString(R.string.test_process, cmdIndex, cmds.size)

                try {
                    updateCmdInPreferences("--ip $proxyIp --port $proxyPort $cmd")
                    startProxyService()
                    waitForProxyToStart()
                } catch (e: Exception) {
                    appendTextToResults("${getString(R.string.test_proxy_error)}\n\n")
                    stopTesting()
                    break
                }

                if (logClickable) {
                    appendLinkToResults("$cmd\n")
                } else {
                    appendTextToResults("$cmd\n")
                }

                val checkResults = checkSitesAsync(sites, requestsCount, fullLog)
                val successfulCount = checkResults.count { it.second == requestsCount }
                val successPercentage = (successfulCount * 100) / sites.size

                if (successPercentage >= 50) {
                    successfulCmds.add(cmd to successPercentage)
                }

                appendTextToResults("$successfulCount/${sites.size} ($successPercentage%)\n\n")

                delay(delay * 1000L)
                stopProxyService()
                waitForProxyToStop()
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
        testJob?.cancel()
        isTesting = false
        startStopButton.text = getString(R.string.test_start)

        lifecycleScope.launch {
            if (isProxyRunning()) {
                stopProxyService()
            }
        }
    }

    private fun appendTextToResults(text: String) {
        resultsTextView.append(text)

        if (isTesting) {
            saveLog(text)
        }

        scrollToBottom()
    }

    private fun appendLinkToResults(text: String) {
        val spannableString = SpannableString(text)
        val options = arrayOf(
            getString(R.string.cmd_history_apply),
            getString(R.string.cmd_history_copy)
        )

        spannableString.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    AlertDialog.Builder(this@TestActivity)
                        .setTitle(getString(R.string.cmd_history_menu))
                        .setItems(options) { _, which ->
                            when (which) {
                                0 -> updateCmdInPreferences(text.trim())
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

        if (isTesting) {
            saveLog("{$text}")
        }

        scrollToBottom()
    }

    private fun scrollToBottom() {
        scrollTextView.post {
            scrollTextView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun updateCmdInPreferences(cmd: String) {
        val sharedPreferences = getPreferences()
        val editor = sharedPreferences.edit()
        editor.putString("byedpi_cmd_args", cmd)
        editor.apply()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("command", text)
        clipboard.setPrimaryClip(clip)
    }

    private suspend fun checkSitesAsync(sites: List<String>,requestsCount: Int, fullLog: Boolean): List<Pair<String, Int>> {
        return sites.map { site ->
            lifecycleScope.async {
                if (!isProxyRunning()) return@async Pair(site, 0)

                val responseCount = checkSiteAccessibility(site, requestsCount)

                if (fullLog) {
                    appendTextToResults("$site - $responseCount/$requestsCount\n")
                }

                Pair(site, responseCount)
            }
        }.awaitAll()
    }

    private suspend fun checkSiteAccessibility(site: String, requestsCount: Int): Int = withContext(Dispatchers.IO) {
        var responseCount = 0
        val formattedUrl =
            if (!site.startsWith("http://") && !site.startsWith("https://")) {
                "https://$site"
            } else {
                site
            }

        repeat(requestsCount) {
            Log.i("CheckSite", "Attempt ${responseCount + 1}/$requestsCount for $site")

            try {
                val url = URL(formattedUrl)
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyIp, proxyPort))
                val connection = url.openConnection(proxy) as? HttpURLConnection

                if (connection != null) {
                    connection.apply {
                        requestMethod = "GET"
                        connectTimeout = 2000
                        readTimeout = 2000
                        instanceFollowRedirects = false
                        connect()
                    }

                    connection.disconnect()

                    responseCount++
                    Log.i("CheckSite", "Good accessing for $site")
                }
            } catch (e: Exception) {
                Log.e("CheckSite", "Error accessing for $site")
            }
        }

        responseCount
    }

    private fun loadSites(): List<String> {
        val userDomains = getPreferences().getBoolean("byedpi_proxytest_userdomains", false)
        return if (userDomains) {
            val domains = getPreferences().getString("byedpi_proxytest_domains", "")
            domains?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        } else {
            val inputStream = assets.open("proxytest_sites.txt")
            inputStream.bufferedReader().useLines { it.toList() }
        }
    }

    private fun loadCmds(): List<String> {
        val userCommands = getPreferences().getBoolean("byedpi_proxytest_usercommands", false)
        return if (userCommands) {
            val commands = getPreferences().getString("byedpi_proxytest_commands", "")
            commands?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        } else {
            val inputStream = assets.open("proxytest_cmds.txt")
            inputStream.bufferedReader().useLines { it.toList() }
        }
    }

    private fun saveLog(log: String) {
        val file = File(filesDir, "proxy_test.log")
        file.appendText(log)
    }

    private fun loadLog(): String {
        val file = File(filesDir, "proxy_test.log")
        return if (file.exists()) {
            file.readText()
        } else {
            ""
        }
    }

    private fun clearLog() {
        val file = File(filesDir, "proxy_test.log")
        file.writeText("")
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
}

