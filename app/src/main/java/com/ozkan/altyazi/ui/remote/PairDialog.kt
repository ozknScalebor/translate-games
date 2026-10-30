package com.ozkan.altyazi.ui.remote

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
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
                        supportingText = { Text(stringResource(R.string.remote_field_account_hint)) },
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
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PsnSignInDialog(onAccountId: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var exchanging by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val locale = remember { Locale.getDefault().let { "${it.language}_${it.country.ifEmpty { "US" }}" } }

    fun finish() {
        CookieManager.getInstance().removeAllCookies(null)
    }

    Dialog(onDismissRequest = { finish(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), color = AppColors.SurfaceHigh, modifier = Modifier.width(560.dp).height(720.dp)) {
            Column {
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.psn_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.psn_hint), style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                    }
                    IconButton(onClick = { finish(); onDismiss() }) { Icon(Icons.Rounded.Close, stringResource(R.string.action_close)) }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AppColors.Primary)
                Box(Modifier.fillMaxSize()) {
                    if (!exchanging && error == null) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                            val code = PsnLogin.codeFrom(request.url.toString()) ?: return false
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
                                            return true
                                        }

                                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { loading = true }
                                        override fun onPageFinished(view: WebView, url: String) { loading = false }
                                    }
                                    loadUrl(PsnLogin.loginUrl(locale))
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (exchanging) {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AppColors.Primary)
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.psn_fetching), color = AppColors.TextSecondary)
                        }
                    }
                    error?.let {
                        Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.psn_failed, it), color = AppColors.Danger)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { error = null; loading = true }) { Text(stringResource(R.string.action_retry)) }
                        }
                    }
                }
            }
        }
    }
}
