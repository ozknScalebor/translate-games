package com.ozkan.altyazi.ui.remote

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ozkan.altyazi.R
import com.ozkan.altyazi.remote.Pairing
import com.ozkan.altyazi.remote.PsnAccountId
import com.ozkan.altyazi.remote.PsnLogin
import com.ozkan.altyazi.remote.RemoteKeys
import com.ozkan.altyazi.ui.theme.AppColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

/** Third-party page that shows the encoded account id for a PSN online id (no sign-in needed). */
private const val ACCOUNT_LOOKUP_URL = "https://psn.flipscreen.games/"

/**
 * Pairs a PS5 with the 8-digit code from PS5 Settings › System › Remote Play › Link Device.
 * The PSN account id can be filled in by signing in to PlayStation, or pasted.
 */
@Composable
fun PairDialog(
    draft: PairDraft,
    savedAccountId: String?,
    pairing: Pairing,
    onPair: (host: String, accountId: String, pin: String, name: String) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    var name by remember { mutableStateOf(draft.name) }
    var host by remember { mutableStateOf(draft.host) }
    var accountId by remember { mutableStateOf(savedAccountId.orEmpty()) }
    var pin by remember { mutableStateOf("") }
    var signingIn by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // The account id is loaded asynchronously; fill it once it arrives unless the user typed one.
    LaunchedEffect(savedAccountId) { if (accountId.isEmpty() && savedAccountId != null) accountId = savedAccountId }
    LaunchedEffect(pairing) { if (pairing is Pairing.Done) onDone() }

    val running = pairing == Pairing.Running
    val hostValid = RemoteKeys.isIpv4(host)
    val accountValid = PsnAccountId.decode(accountId) != null
    val pinValid = RemoteKeys.isPairingPin(pin)

    Dialog(onDismissRequest = { if (!running) onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = AppColors.SurfaceHigh,
            modifier = Modifier.width(620.dp).imePadding(),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pair_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, stringResource(R.string.action_close)) }
                }
                Spacer(Modifier.height(12.dp))
                Steps()
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME) },
                    label = { Text(stringResource(R.string.remote_field_name)) },
                    placeholder = { Text(stringResource(R.string.remote_field_name_hint)) },
                    singleLine = true,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.filter { c -> c.isDigit() || c == '.' }.take(15) },
                    label = { Text(stringResource(R.string.remote_field_host)) },
                    supportingText = { Text(stringResource(R.string.remote_field_host_hint)) },
                    isError = host.isNotEmpty() && !hostValid,
                    singleLine = true,
                    enabled = !running,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Top) {
                    OutlinedTextField(
                        value = accountId,
                        onValueChange = { accountId = it.trim().take(16) },
                        label = { Text(stringResource(R.string.remote_field_account)) },
                        supportingText = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.remote_field_account_hint), modifier = Modifier.weight(1f, fill = false))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.remote_find_by_name),
                                    color = AppColors.Primary,
                                    modifier = Modifier.clickable { openInBrowser(context, ACCOUNT_LOOKUP_URL) },
                                )
                            }
                        },
                        isError = accountId.isNotEmpty() && !accountValid,
                        trailingIcon = { if (accountValid) Icon(Icons.Rounded.CheckCircle, null, tint = AppColors.Success) },
                        singleLine = true,
                        enabled = !running,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = { signingIn = true }, enabled = !running, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.Login, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.remote_psn_sign_in))
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                    label = { Text(stringResource(R.string.remote_field_pin)) },
                    supportingText = { Text(stringResource(R.string.remote_field_pin_hint)) },
                    singleLine = true,
                    enabled = !running,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )

                (pairing as? Pairing.Failed)?.let { failed ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.pair_failed) + (failed.message?.let { "\n$it" } ?: ""),
                        color = AppColors.Danger,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(Modifier.height(20.dp))
                if (running) LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)), color = AppColors.Primary)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                    Button(
                        onClick = { onPair(host, accountId, pin, name) },
                        enabled = !running && hostValid && accountValid && pinValid,
                    ) { Text(stringResource(if (running) R.string.pair_running else R.string.pair_action)) }
                }
            }
        }
    }

    if (signingIn) {
        PsnSignInDialog(
            onAccountId = { id ->
                accountId = id
                signingIn = false
            },
            onDismiss = { signingIn = false },
        )
    }
}

@Composable
private fun Steps() {
    Surface(shape = RoundedCornerShape(18.dp), color = AppColors.Surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Step(1, stringResource(R.string.pair_step1))
            Step(2, stringResource(R.string.pair_step2))
            Step(3, stringResource(R.string.pair_step3))
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(AppColors.Primary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = AppColors.Primary, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Sony's own sign-in page in a WebView. When it redirects back with a one-time code, the code is
 * exchanged for the account id and the session cookies are cleared again.
 *
 * Fallback for when the page stays blank in the WebView: sign in in the regular browser, then paste
 * the address of the "redirect" page it ends on; the code is read from that address.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PsnSignInDialog(onAccountId: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var exchanging by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pastedUrl by remember { mutableStateOf("") }
    val locale = remember { Locale.getDefault().let { "${it.language}_${it.country.ifEmpty { "US" }}" } }
    val loginUrl = remember(locale) { PsnLogin.loginUrl(locale) }

    fun finish() {
        CookieManager.getInstance().removeAllCookies(null)
    }

    /** Exchanges a code once; later redirects carrying the same code are ignored. */
    fun exchange(code: String) {
        if (exchanging) return
        exchanging = true
        scope.launch {
            try {
                onAccountId(PsnLogin.accountId(code))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
                exchanging = false
            } finally {
                finish()
            }
        }
    }

    Dialog(onDismissRequest = { finish(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), color = AppColors.SurfaceHigh, modifier = Modifier.width(600.dp).height(760.dp).imePadding()) {
            Column {
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.psn_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.psn_hint), style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                    }
                    IconButton(onClick = { finish(); onDismiss() }) { Icon(Icons.Rounded.Close, stringResource(R.string.action_close)) }
                }
                if (loading && !exchanging) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AppColors.Primary)
                Box(Modifier.weight(1f).fillMaxWidth().background(Color.White)) {
                    if (!exchanging && error == null) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.javaScriptCanOpenWindowsAutomatically = true
                                    // Sony's sign-in serves a blank page to embedded browsers: present
                                    // the WebView as the regular Chrome it is built on.
                                    settings.userAgentString = settings.userAgentString
                                        .replace("; wv", "")
                                        .replace(Regex("""Version/\d+(\.\d+)* """), "")
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                            val code = PsnLogin.codeFrom(request.url.toString()) ?: return false
                                            exchange(code)
                                            return true
                                        }

                                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                            loading = true
                                            // Server-side redirects do not always pass through shouldOverrideUrlLoading.
                                            PsnLogin.codeFrom(url)?.let { view.stopLoading(); exchange(it) }
                                        }

                                        override fun onPageFinished(view: WebView, url: String) { loading = false }
                                    }
                                    loadUrl(loginUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (exchanging) {
                        Column(Modifier.fillMaxSize().background(AppColors.SurfaceHigh), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AppColors.Primary)
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.psn_fetching), color = AppColors.TextSecondary)
                        }
                    }
                    error?.let {
                        Column(Modifier.fillMaxSize().background(AppColors.SurfaceHigh).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.psn_failed, it), color = AppColors.Danger)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { error = null; loading = true }) { Text(stringResource(R.string.action_retry)) }
                        }
                    }
                }

                // Fallback: sign in in the browser and paste the final address.
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.psn_blank_title), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.psn_blank_body), style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { openInBrowser(context, loginUrl) }) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.psn_open_browser))
                        }
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = pastedUrl,
                            onValueChange = { text ->
                                pastedUrl = text.trim()
                                PsnLogin.codeFrom(pastedUrl)?.let(::exchange)
                            },
                            label = { Text(stringResource(R.string.psn_paste_url)) },
                            isError = pastedUrl.isNotEmpty() && PsnLogin.codeFrom(pastedUrl) == null,
                            singleLine = true,
                            enabled = !exchanging,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

internal fun openInBrowser(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
