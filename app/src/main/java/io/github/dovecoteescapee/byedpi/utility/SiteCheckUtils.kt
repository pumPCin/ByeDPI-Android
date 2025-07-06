package io.github.dovecoteescapee.byedpi.utility

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

class SiteCheckUtils(
    private val proxyIp: String,
    private val proxyPort: Int
) {

    suspend fun checkSitesAsync(
        sites: List<String>,
        requestsCount: Int,
        fullLog: Boolean,
        onSiteChecked: ((String, Int, Int) -> Unit)? = null
    ): List<Pair<String, Int>> {
        return withContext(Dispatchers.IO) {
            sites.map { site ->
                async {
                    val successCount = checkSiteAccess(site, requestsCount)
                    if (fullLog) {
                        onSiteChecked?.invoke(site, successCount, requestsCount)
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
            val url = URL(formattedUrl)
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyIp, proxyPort))
            val connection = url.openConnection(proxy) as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 2000
                connection.readTimeout = 2000

                val responseCode = connection.responseCode
                responseCount++
            } catch (e: Exception) {
            } finally {
                connection.disconnect()
            }
        }
        responseCount
    }
}