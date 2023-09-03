package com.badmanners.murglar.client.cli.logger

import com.badmanners.murglar.lib.core.log.LoggerMiddleware


class StdLogger : LoggerMiddleware {

    override fun v(tag: String, message: String?) = print(tag, message)
    override fun v(tag: String, tr: Throwable?) = print(tag, tr)
    override fun v(tag: String, message: String?, tr: Throwable?) = print(tag, message, tr)

    override fun d(tag: String, message: String?) = print(tag, message)
    override fun d(tag: String, tr: Throwable?) = print(tag, tr)
    override fun d(tag: String, message: String?, tr: Throwable?) = print(tag, message, tr)

    override fun i(tag: String, message: String?) = print(tag, message)
    override fun i(tag: String, tr: Throwable?) = print(tag, tr)
    override fun i(tag: String, message: String?, tr: Throwable?) = print(tag, message, tr)

    override fun w(tag: String, message: String?) = print(tag, message)
    override fun w(tag: String, tr: Throwable?) = print(tag, tr)
    override fun w(tag: String, message: String?, tr: Throwable?) = print(tag, message, tr)

    override fun e(tag: String, message: String?) = print(tag, message)
    override fun e(tag: String, tr: Throwable?) = print(tag, tr)
    override fun e(tag: String, message: String?, tr: Throwable?) = print(tag, message, tr)

    override fun crashlyticsLog(tag: String, message: String?) = print(tag, message)
    override fun crashlyticsLog(tag: String, message: String?, tr: Throwable?) = print(tag, message, tr)
    override fun dump(tag: String, largeText: String?) = print(tag, largeText)

    private fun print(tag: String, tr: Throwable?) = print(tag, null, tr)

    private fun print(tag: String, message: String?, tr: Throwable? = null) {
        println("$tag: $message")
        tr?.printStackTrace()
    }
}