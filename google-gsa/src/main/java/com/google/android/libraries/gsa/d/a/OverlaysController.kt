package com.google.android.libraries.gsa.d.a

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.SparseArray
import androidx.core.util.forEach

abstract class OverlaysController(private val service: Service) {

    private val clientBinders = SparseArray<OverlayControllerBinder>()
    val handler = Handler(Looper.getMainLooper())

    abstract fun createController(
        configuration: Configuration?,
        serverVersion: Int,
        clientVersion: Int,
    ): OverlayController

    @Synchronized
    fun onBind(intent: Intent): IBinder? {
        val data = intent.data ?: return null

        val port = data.port
        if (port == -1) return null

        val version = parseQueryParam(data, "v", Int.MAX_VALUE)
        val clientVersion = parseQueryParam(data, "cv", Int.MAX_VALUE)

        val host = data.host
        val packagesForUid = getPackagesForUid(port)

        if (host == null || packagesForUid == null || !packagesForUid.contains(host)) {
            return null
        }

        var binder = clientBinders.get(port)

        if (binder != null && binder.serverVersion != version) {
            binder.destroy()
            binder = null
        }

        if (binder == null) {
            binder = OverlayControllerBinder(this, port, host, version, clientVersion)
            clientBinders.put(port, binder)
        }

        return binder
    }

    @Synchronized
    fun onUnbind(intent: Intent) {
        val data = intent.data ?: return

        val port = data.port
        if (port != -1) {
            clientBinders.get(port)?.destroy()
            clientBinders.remove(port)
        }
    }

    @Synchronized
    fun onDestroy() {
        clientBinders.forEach { _, binder -> binder.destroy() }
        clientBinders.clear()
    }

    open val defaultVersion: Int get() = 24

    private fun parseQueryParam(uri: Uri, key: String, defaultValue: Int): Int = try {
        uri.getQueryParameter(key)!!.toInt()
    } catch (_: Exception) {
        defaultValue
    }

    private fun getPackagesForUid(uid: Int): List<String>? =
        service.packageManager.getPackagesForUid(uid)?.asList()
}
