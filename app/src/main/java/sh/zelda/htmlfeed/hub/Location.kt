package sh.zelda.htmlfeed.hub

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import sh.zelda.htmlfeed.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Somewhere to get a forecast for: coordinates, and a name to title the card with. */
data class WeatherPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Where the weather module gets its coordinates. There's no saved city: the panel reports
 * wherever the phone is, same as the system weather app a tap on the card hands off to.
 */
object LocationRepository {
    /** Coarse is accurate to a few kilometres, which is a forecast's resolution anyway. */
    const val PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

    /**
     * And this one, which is the difference between the panel working and not.
     *
     * The -1 panel is drawn by a service the launcher binds, so this app never has anything the
     * system counts as on screen while the panel is open. The process sits at bound-top without
     * the location capability - the launcher doesn't bind with BIND_INCLUDE_CAPABILITIES - so a
     * foreground-only grant has every read rejected by appops, last known fix included. "Allow
     * all the time" is what lifts that.
     */
    const val BACKGROUND_PERMISSION = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    /** Older than this and it's worth waiting for a provider before drawing a forecast on it. */
    private val FRESH_MS = TimeUnit.MINUTES.toMillis(10)

    /** The panel is open while this runs, so it can't be long. */
    private val FIX_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10)

    fun hasPermission(context: Context): Boolean = granted(context, PERMISSION)

    /** Whether a fix can be had from the panel itself, rather than only from the settings app. */
    fun hasBackgroundPermission(context: Context): Boolean =
        hasPermission(context) && granted(context, BACKGROUND_PERMISSION)

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * The phone's position, remembered across calls. Null only when there's no permission and
     * nothing was ever stored.
     *
     * Without [BACKGROUND_PERMISSION] every read from the panel is rejected, so the stored fix
     * is all the weather card has to go on; the settings screen refreshes it on each visit,
     * where the app really is in the foreground and a read is allowed.
     */
    suspend fun current(context: Context): WeatherPlace? =
        locate(context)?.also { Settings.setLastKnownPlace(context, it) }
            ?: Settings.lastKnownPlace(context)

    private suspend fun locate(context: Context): WeatherPlace? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val cached = lastKnown(manager)
        // A stale fix still beats an empty card, so it stays as the fallback for a request that
        // times out - someone indoors with no network provider would otherwise get nothing.
        val fix = cached?.takeIf { System.currentTimeMillis() - it.time < FRESH_MS }
            ?: requestFix(context, manager)
            ?: cached
            ?: return null
        return WeatherPlace(
            name = locality(context, fix) ?: "Current location",
            latitude = fix.latitude,
            longitude = fix.longitude,
        )
    }

    /** The freshest fix any provider already has, which usually costs nothing to ask for. */
    private fun lastKnown(manager: LocationManager): Location? =
        manager.allProviders
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }

    private suspend fun requestFix(context: Context, manager: LocationManager): Location? {
        val provider = provider(manager) ?: return null
        return withTimeoutOrNull(FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                runCatching {
                    LocationManagerCompat.getCurrentLocation(
                        manager,
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(context),
                    ) { location -> if (continuation.isActive) continuation.resume(location) }
                }.onFailure { if (continuation.isActive) continuation.resume(null) }
            }
        }
    }

    /**
     * Fused first where there is one, then the network provider. Never GPS: a coarse-only grant
     * isn't allowed to ask it, and a forecast doesn't need a rooftop-accurate fix to begin with.
     */
    private fun provider(manager: LocationManager): String? {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        return candidates.firstOrNull { candidate ->
            runCatching { manager.isProviderEnabled(candidate) }.getOrDefault(false)
        }
    }

    /** A town name for the card's title. Blocking, network-backed, and allowed to fail. */
    private suspend fun locality(context: Context, fix: Location): String? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context).getFromLocation(fix.latitude, fix.longitude, 1)
                    ?.firstOrNull()
                    ?.let { it.locality ?: it.subAdminArea ?: it.adminArea ?: it.countryName }
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }
}
