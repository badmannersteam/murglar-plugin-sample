package com.badmanners.murglar.client.cli.notification

import com.badmanners.murglar.lib.core.notification.NotificationMiddleware


class StdNotificationMiddleware : NotificationMiddleware {
    override fun shortNotify(text: String) = println(text)
    override fun longNotify(text: String) = println(text)
}