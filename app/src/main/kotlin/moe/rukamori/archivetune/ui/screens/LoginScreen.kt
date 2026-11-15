/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import timber.log.Timber
import moe.rukamori.archivetune.utils.AuthDomainValidator
import moe.rukamori.archivetune.utils.HttpRedactor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.resetAuthWebViewSession
import moe.rukamori.archivetune.viewmodels.LoginEvent
import moe.rukamori.archivetune.viewmodels.LoginViewModel

const val LOGIN_ROUTE = "login"
const val LOGIN_URL_ARGUMENT = "url"

fun buildLoginRoute(startUrl: String? = null): String {
    val resolvedUrl = startUrl?.trim().takeUnless { it.isNullOrBlank() } ?: return LOGIN_ROUTE
    return "$LOGIN_ROUTE?$LOGIN_URL_ARGUMENT=${Uri.encode(resolvedUrl)}"
}

private const val DEFAULT_LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

private val YOUTUBE_COOKIE_URLS =
    listOf(
        "https://music.youtube.com",
        "https://www.youtube.com",
        "https://youtube.com",
    )

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    startUrl: String? = null,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    @Suppress("UNUSED_VARIABLE")
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val loginSuccessMessage = stringResource(R.string.login_success)
    var webView: WebView? = null

    LaunchedEffect(viewModel, loginSuccessMessage) {
        viewModel.events.collect { event ->
            when (event) {
                LoginEvent.Completed -> {
                    Toast.makeText(context, loginSuccessMessage, Toast.LENGTH_SHORT).show()
                    navController.navigateUp()
                }
            }
        }
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        AndroidView(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(0)
                        val cookieManager = CookieManager.getInstance()
                        webViewClient =
                            object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val destinationUrl = request?.url?.toString()
                                    if (!AuthDomainValidator.isApprovedAuthUrl(destinationUrl)) {
                                        Timber.w(
                                            "LoginScreen: blocked navigation to unapproved host: %s (unapproved domain)",
                                            HttpRedactor.sanitizeUrlForLogging(destinationUrl),
                                        )
                                        return true
                                    }
                                    return false
                                }

                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    if (!AuthDomainValidator.isApprovedAuthUrl(url)) {
                                        Timber.w(
                                            "LoginScreen: blocked navigation to unapproved host: %s (unapproved domain)",
                                            HttpRedactor.sanitizeUrlForLogging(url),
                                        )
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    if (!AuthDomainValidator.isApprovedAuthUrl(url)) {
                                        Timber.w(
                                            "LoginScreen: unapproved origin detected onPageStarted: %s (stopping load)",
                                            HttpRedactor.sanitizeUrlForLogging(url),
                                        )
                                        view?.stopLoading()
                                    }
                                }

                                override fun onPageFinished(
                                    view: WebView,
                                    url: String?,
                                ) {
                                    if (!AuthDomainValidator.isApprovedAuthUrl(url)) {
                                        return
                                    }
                                    val isYouTubePage = AuthDomainValidator.isApprovedYouTubeUrl(url)
                                    if (isYouTubePage) {
                                        view.evaluateJavascript(
                                            "(function(){try{var c=window.ytcfg;if(c&&c.get){var v=c.get('VISITOR_DATA');if(v){return v;}}var y=window.yt&&window.yt.config_;if(y&&y.VISITOR_DATA){return y.VISITOR_DATA;}var s=document.querySelectorAll('script');for(var i=0;i<s.length;i++){var m=s[i].textContent.match(/\"VISITOR_DATA\":\"([^\"]+)\"/);if(m){return m[1];}}}catch(e){}return '';})()",
                                        ) { result ->
                                            val visitorData = AuthDomainValidator.parseJsResult(result)
                                            if (visitorData.isNotBlank()) {
                                                viewModel.onVisitorDataExtracted(visitorData)
                                            }
                                        }
                                        view.evaluateJavascript(
                                            "(function(){try{var c=window.ytcfg;if(c&&c.get){var d=c.get('DATASYNC_ID');if(d){return d;}}var y=window.yt&&window.yt.config_;if(y&&y.DATASYNC_ID){return y.DATASYNC_ID;}var s=document.querySelectorAll('script');for(var i=0;i<s.length;i++){var m=s[i].textContent.match(/[\\\"'](?:DATASYNC_ID|dataSyncId)[\\\"']\\s*:\\s*[\\\"']([^\\\"']+)[\\\"']/);if(m){return m[1];}}}catch(e){}return '';})()",
                                        ) { result ->
                                            val dataSyncId = AuthDomainValidator.parseJsResult(result)
                                            if (dataSyncId.isNotBlank()) {
                                                viewModel.onDataSyncIdExtracted(dataSyncId)
                                            }
                                        }
                                        view.evaluateJavascript(
                                            "(function(){try{var c=window.ytcfg;if(c&&c.get){var t=c.get('PO_TOKEN');if(t){return t;}}var s=document.querySelectorAll('script');for(var i=0;i<s.length;i++){var m=s[i].textContent.match(/\"PO_TOKEN\":\"([^\"]+)\"/);if(m){return m[1];}}}catch(e){}return '';})()",
                                        ) { result ->
                                            val poToken = AuthDomainValidator.parseJsResult(result)
                                            if (poToken.isNotBlank()) {
                                                viewModel.onPoTokenExtracted(poToken)
                                            }
                                        }
                                    }

                                    mergeYouTubeCookies(cookieManager, url)?.let { cookie ->
                                        viewModel.onCookiesCaptured(cookie)
                                    }
                                }

                                override fun doUpdateVisitedHistory(
                                    view: WebView?,
                                    url: String?,
                                    isReload: Boolean,
                                ) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    mergeYouTubeCookies(cookieManager, url)?.let { cookie ->
                                        viewModel.onCookiesCaptured(cookie)
                                    }
                                }
                            }
                        val defaultUa = android.webkit.WebSettings.getDefaultUserAgent(context)
                        val cleanUa = defaultUa
                            .replace("; wv", "")
                            .replace(Regex("Version/\\d+\\.\\d+\\s?"), "")
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            userAgentString = cleanUa
                        }
                        val targetUrl = startUrl?.takeIf { AuthDomainValidator.isApprovedAuthUrl(it) } ?: DEFAULT_LOGIN_URL
                        loadUrl(targetUrl)
                        webView = this
                    }
                },
            )
        }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}

private fun mergeYouTubeCookies(
    cookieManager: CookieManager,
    currentUrl: String? = null,
): String? {
    val cookieParts = linkedMapOf<String, String>()
    val candidateUrls = linkedSetOf<String>()

    currentUrl.toYouTubeCookieOrigin()?.let(candidateUrls::add)
    candidateUrls.addAll(YOUTUBE_COOKIE_URLS)

    cookieManager.flush()

    candidateUrls.forEach { url ->
        cookieManager
            .getCookie(url)
            ?.split(";")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.forEach { part ->
                val separatorIndex = part.indexOf('=')
                if (separatorIndex <= 0) return@forEach

                val key = part.substring(0, separatorIndex).trim()
                val value = part.substring(separatorIndex + 1).trim()
                if (key.isNotEmpty()) {
                    cookieParts[key] = value
                }
            }
    }

    return cookieParts
        .takeIf { it.isNotEmpty() }
        ?.entries
        ?.joinToString(separator = "; ") { (key, value) -> "$key=$value" }
}

private fun String?.toYouTubeCookieOrigin(): String? {
    val parsed = this?.let(Uri::parse) ?: return null
    val host = parsed.host?.lowercase() ?: return null
    if (host != "youtube.com" && !host.endsWith(".youtube.com")) return null

    val scheme =
        parsed.scheme
            ?.takeIf { it.equals("https", ignoreCase = true) }
            ?: return null

    return "$scheme://$host"
}
