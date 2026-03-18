
package com.trackflow.core.context

import android.content.Context
import android.os.Build

class DefaultContextProvider(private val context: Context) {

    fun context(): Map<String, Any?> {
        return mapOf(
            "platform" to "android",
            "device_model" to Build.MODEL,
            "os_version" to Build.VERSION.RELEASE,
            "package_name" to context.packageName
        )
    }
}
