package com.badmanners.murglar.lib.sample.localization

import com.badmanners.murglar.lib.core.localization.Messages


interface SampleMessages : Messages {
    val oauthToken: String
    val twoFAText: String
    val illegalOauthTokenFormat: String
    val illegalCookieFormat: String
    val radio: String
}