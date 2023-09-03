package com.badmanners.murglar.lib.sample.login

import com.badmanners.murglar.lib.core.localization.MessageException
import com.badmanners.murglar.lib.core.login.CaptchaRequiredStep
import com.badmanners.murglar.lib.core.login.CredentialLoginStep
import com.badmanners.murglar.lib.core.login.CredentialsLoginVariant
import com.badmanners.murglar.lib.core.login.CredentialsLoginVariant.Credential
import com.badmanners.murglar.lib.core.login.LoginResolver
import com.badmanners.murglar.lib.core.login.SuccessfulLogin
import com.badmanners.murglar.lib.core.login.TwoFARequiredStep
import com.badmanners.murglar.lib.core.login.WebLoginVariant
import com.badmanners.murglar.lib.core.network.NetworkMiddleware
import com.badmanners.murglar.lib.core.network.NetworkRequest
import com.badmanners.murglar.lib.core.network.ResponseConverters
import com.badmanners.murglar.lib.core.notification.NotificationMiddleware
import com.badmanners.murglar.lib.core.preference.PreferenceMiddleware
import com.badmanners.murglar.lib.core.utils.MurglarLibUtils
import com.badmanners.murglar.lib.core.utils.MurglarLibUtils.setHttpOnlySafely
import com.badmanners.murglar.lib.core.utils.contract.WorkerThread
import com.badmanners.murglar.lib.core.utils.getJsonObject
import com.badmanners.murglar.lib.core.utils.getString
import com.badmanners.murglar.lib.core.utils.getStringOpt
import com.badmanners.murglar.lib.core.utils.has
import com.badmanners.murglar.lib.core.webview.WebViewProvider
import com.badmanners.murglar.lib.core.webview.WebViewProvider.UrlLoadPolicy.ALLOW_LOAD
import com.badmanners.murglar.lib.core.webview.WebViewProvider.UrlLoadPolicy.ALLOW_LOAD_AND_FINISH
import com.badmanners.murglar.lib.core.webview.WebViewProvider.UrlLoadPolicy.DISCARD_LOAD
import com.badmanners.murglar.lib.core.webview.WebViewProvider.UrlLoadPolicyResolver
import com.badmanners.murglar.lib.sample.SampleMurglar
import com.badmanners.murglar.lib.sample.localization.SampleMessages
import org.apache.commons.lang3.StringUtils
import org.threeten.bp.Duration
import java.net.HttpCookie


class SampleLoginResolver(
    private val preferences: PreferenceMiddleware,
    private val network: NetworkMiddleware,
    private val notifications: NotificationMiddleware,
    private val murglar: SampleMurglar,
    private val messages: SampleMessages
) : LoginResolver {

    companion object {
        const val WEB_LOGIN_VARIANT_1 = "web_login_1"
        const val WEB_LOGIN_VARIANT_2 = "web_login_2"

        const val TOKEN_LOGIN_VARIANT = "token_login"
        const val OAUTH_TOKEN_CREDENTIAL = "oauth-token"

        const val EMAIL_LOGIN_VARIANT = "email_login"
        const val EMAIL_CREDENTIAL = "email"
        const val PASSWORD_CREDENTIAL = "password"

        const val COOKIE_LOGIN_VARIANT = "cookie_login"
        const val COOKIE_CREDENTIAL = "cookie"

        private const val CAPTCHA_SID = "captcha_sid"

        private const val USERNAME_PREFERENCE = "username"
        private const val OAUTH_TOKEN_PREFERENCE = "oauth-token"

        private const val NO_VALUE = "no_value"
    }

    override val isLogged: Boolean
        get() = oauthToken != NO_VALUE

    override val loginInfo: String
        get() = when {
            isLogged -> "${messages.youAreLoggedIn}: $username"
            else -> messages.youAreNotLoggedIn
        }

    val oauthToken: String
        get() = preferences.getString(OAUTH_TOKEN_PREFERENCE, NO_VALUE)

    val username: String
        get() = preferences.getString(USERNAME_PREFERENCE, NO_VALUE)


    override val webLoginVariants = listOf(
        WebLoginVariant(
            id = WEB_LOGIN_VARIANT_1,
            label = { "${messages.loginWith(web = true)} v1" }
        ),
        WebLoginVariant(
            id = WEB_LOGIN_VARIANT_2,
            label = { "${messages.loginWith(web = true)} v2" }
        )
    )

    @WorkerThread
    override fun webLogin(loginVariantId: String, webViewProvider: WebViewProvider): Boolean {
        logout()

        val startUrl = when (loginVariantId) {
            WEB_LOGIN_VARIANT_1 -> "https://sample.com/signin"
            WEB_LOGIN_VARIANT_2 -> "https://alternative.sample.com/signin"
            else -> error("Unknown loginVariantId: $loginVariantId!")
        }

        val success = webViewProvider.startWebView(
            enableJS = true,
            userAgent = MurglarLibUtils.CHROME_USER_AGENT,
            helpText = messages.loginHelpText,
            startUrl = startUrl,
            domainsForCookiesSync = listOf("https://sample.com/"),
            resolver = object : UrlLoadPolicyResolver {
                override fun resolveUrlLoadPolicy(url: String) = when {
                    url.contains("sample.com") -> ALLOW_LOAD
                    else -> DISCARD_LOAD
                }

                override fun resolveResourceLoadPolicy(url: String) = when {
                    url.contains("sample.com/oauth/token") -> ALLOW_LOAD_AND_FINISH
                    else -> ALLOW_LOAD
                }
            }
        )

        if (success)
            try {
                val token = network.getCookie("sample.com", "oauth_token")!!.value
                preferences.setString(OAUTH_TOKEN_PREFERENCE, token)
                updateUser()
            } catch (e: Exception) {
                logout()
                throw e
            }
        else
            logout()

        return success
    }

    override val credentialsLoginVariants = listOf(
        CredentialsLoginVariant(
            id = TOKEN_LOGIN_VARIANT,
            label = { messages.loginWith(tokens = true) },
            credentials = listOf(
                Credential(OAUTH_TOKEN_CREDENTIAL, messages::oauthToken)
            )
        ),
        CredentialsLoginVariant(
            id = EMAIL_LOGIN_VARIANT,
            label = { messages.loginWith(email = true) },
            credentials = listOf(
                Credential(EMAIL_CREDENTIAL, { "Email" }),
                Credential(PASSWORD_CREDENTIAL, messages::password)
            )
        ),
        CredentialsLoginVariant(
            id = COOKIE_LOGIN_VARIANT,
            label = { messages.loginWith(cookies = true) },
            credentials = listOf(
                Credential(COOKIE_CREDENTIAL, { "Cookie" })
            )
        )
    )

    @WorkerThread
    override fun credentialsLogin(loginVariantId: String, args: Map<String, String>): CredentialLoginStep {
        logout()
        return when (loginVariantId) {
            TOKEN_LOGIN_VARIANT -> tokenLogin(args)
            EMAIL_LOGIN_VARIANT -> emailLogin(args)
            COOKIE_LOGIN_VARIANT -> cookieLogin(args)
            else -> error("Unknown loginVariantId: $loginVariantId!")
        }
    }

    private fun tokenLogin(args: Map<String, String>): CredentialLoginStep {
        val token = args[OAUTH_TOKEN_CREDENTIAL]
        check(token != null && token.matches("\\d-\\d+-\\d+-\\w+".toRegex())) {
            messages.illegalOauthTokenFormat
        }

        try {
            preferences.setString(OAUTH_TOKEN_PREFERENCE, token)
            updateUser()
        } catch (e: Exception) {
            logout()
            throw e
        }

        return SuccessfulLogin
    }

    private fun emailLogin(args: Map<String, String>): CredentialLoginStep {
        val email = args[EMAIL_CREDENTIAL]
        val password = args[PASSWORD_CREDENTIAL]

        check(!email.isNullOrEmpty() && email.contains('@') && !password.isNullOrEmpty()) {
            messages.invalidCredentialsFormat
        }

        val request = NetworkRequest.Builder("https://sample.com/api/signin", "POST")
            .addParameter("email", email)
            .addParameter("password", password)
            .apply {
                val captchaSid = args[CAPTCHA_SID]
                val captchaValue = args[CaptchaRequiredStep.CAPTCHA_VALUE]
                if (!captchaValue.isNullOrEmpty() && !captchaSid.isNullOrEmpty()) {
                    addParameter("captcha_sid", captchaSid)
                    addParameter("captcha_value", captchaValue)
                }

                val twoFA = args[TwoFARequiredStep.TWO_F_A_VALUE]
                if (!twoFA.isNullOrEmpty())
                    addParameter("2fa", twoFA)
            }
            .build()

        val response = network.execute(request, ResponseConverters.asJsonObject())
        val result = response.result

        if (response.statusCode == 403 && result.getStringOpt("error") == "invalid-credentials")
            error(messages.invalidCredentialsFormat)
        check(response.isSuccessful) { "Error logging in: $result" }

        val jsonResult = result.getJsonObject("result")
        return when {
            jsonResult.has("captcha") -> {
                val captchaImageUrl = jsonResult.getJsonObject("captcha").getString("imageUrl")

                val captchaSid = jsonResult.getJsonObject("captcha").getString("sid")
                val callbackArgs = args + (CAPTCHA_SID to captchaSid)

                CaptchaRequiredStep(captchaImageUrl, callbackArgs)
            }

            jsonResult.has("2fa") -> TwoFARequiredStep(messages.twoFAText, args)

            else -> {
                val token = jsonResult.getString("token")
                try {
                    preferences.setString(OAUTH_TOKEN_PREFERENCE, token)
                    updateUser()
                } catch (e: Exception) {
                    logout()
                    throw e
                }
                SuccessfulLogin
            }
        }
    }

    private fun cookieLogin(args: Map<String, String>): CredentialLoginStep {
        val cookieValue = args[COOKIE_CREDENTIAL]
        check(cookieValue != null && cookieValue.length == 192 && StringUtils.isAlphanumeric(cookieValue)) {
            messages.illegalCookieFormat
        }

        val cookie = HttpCookie("session", cookieValue).apply {
            domain = ".sample.com"
            path = "/"
            maxAge = Duration.ofDays(180).seconds
            setHttpOnlySafely(true)
            secure = true
        }

        network.addCookie(cookie)

        val request = NetworkRequest.Builder("https://sample.com/api/getToken").build()
        val response = network.execute(request, ResponseConverters.asJsonObject())
        val result = response.result

        if (response.statusCode == 403)
            error(messages.invalidCredentialsFormat)
        check(response.isSuccessful) { "Error logging in: $result" }

        val token = result.getJsonObject("result").getString("token")
        try {
            preferences.setString(OAUTH_TOKEN_PREFERENCE, token)
            updateUser()
        } catch (e: Exception) {
            logout()
            throw e
        }

        return SuccessfulLogin
    }

    override fun logout() {
        network.clearAllCookies()
        preferences.remove(OAUTH_TOKEN_PREFERENCE)
        preferences.remove(USERNAME_PREFERENCE)
    }

    fun updateUser() = murglar.loadUsername()
        ?.let { preferences.setString(USERNAME_PREFERENCE, it) }
        ?: throw MessageException(messages.sessionUpdateFailedWithServiceName)

    fun checkLogged() {
        if (isLogged)
            return
        notifications.shortNotify(messages.youAreNotLoggedIn)
        throw MessageException(messages.youAreNotLoggedIn)
    }
}