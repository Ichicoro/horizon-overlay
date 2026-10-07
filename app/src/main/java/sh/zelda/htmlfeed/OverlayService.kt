package sh.zelda.htmlfeed

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.os.IBinder
import com.google.android.libraries.gsa.d.a.OverlayController
import com.google.android.libraries.gsa.d.a.OverlaysController

class OverlayService : Service() {
    private lateinit var overlaysController: OverlaysController

    override fun onCreate() {
        super.onCreate()
        overlaysController = object : OverlaysController(this) {
            override fun createController(
                configuration: Configuration?,
                serverVersion: Int,
                clientVersion: Int
            ): OverlayController {
                val ctx = configuration?.let { createConfigurationContext(it) } ?: this@OverlayService
                return HubOverlay(ctx)
            }
        }
    }

    override fun onDestroy() {
        overlaysController.onDestroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = overlaysController.onBind(intent)

    override fun onUnbind(intent: Intent): Boolean {
        overlaysController.onUnbind(intent)
        return false
    }
}
