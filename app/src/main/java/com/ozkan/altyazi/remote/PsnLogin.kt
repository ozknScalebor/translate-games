package com.ozkan.altyazi.remote

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

/**
 * Finds the PSN account id after the user signs in to PlayStation in a WebView, using the same
 * OAuth flow as the official Remote Play app (as documented by chiaki-ng's psn-account-id script).
 * The password is entered on Sony's page only; this app just reads the one-time code from the
 * redirect and exchanges it for the account id. No token is stored.
 */
object PsnLogin {
    private const val CLIENT_ID = "ba495a24-818c-472b-b12d-ff231c1b5745"
    private const val CLIENT_SECRET = "mvaiZkRsAsI1IBkY"
    const val REDIRECT_URI = "https://remoteplay.dl.playstation.net/remoteplay/redirect"
    private const val TOKEN_URL = "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/token"

    fun loginUrl(locale: String): String =
        "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/authorize" +
            "?service_entity=urn:service-entity:psn&response_type=code&client_id=$CLIENT_ID" +
            "&redirect_uri=$REDIRECT_URI&scope=psn:clientapp&request_locale=$locale&ui=pr&service_logo=ps" +
            "&layout_type=popup&smcid=remoteplay&prompt=always&PlatformPrivacyWs1=minimal"

    /** The authorization code when [url] is the redirect after a successful sign-in. */
    fun codeFrom(url: String): String? =
        if (url.startsWith(REDIRECT_URI)) Uri.parse(url).getQueryParameter("code")?.takeIf { it.isNotBlank() } else null

    /** Exchanges the code for a token and returns the Base64 account id. */
    suspend fun accountId(code: String): String = withContext(Dispatchers.IO) {
        val auth = "Basic " + Base64.getEncoder().encodeToString("$CLIENT_ID:$CLIENT_SECRET".toByteArray())
        val body = "grant_type=authorization_code&code=${URLEncoder.encode(code, "UTF-8")}&redirect_uri=$REDIRECT_URI"

        val token = request(TOKEN_URL, auth, body).getString("access_token")
        val account = request("$TOKEN_URL/${URLEncoder.encode(token, "UTF-8")}", auth, null)
        PsnAccountId.fromUserId(account.getString("user_id").toLong())
    }

    private fun request(url: String, auth: String, formBody: String?): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", auth)
            if (formBody != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.outputStream.use { it.write(formBody.toByteArray(Charsets.US_ASCII)) }
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("PlayStation sign-in failed (HTTP $code)")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
