package com.google.android.libraries.launcherclient

import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.view.WindowManager.LayoutParams
import com.google.android.binder.LauncherOverlayBinder
import com.google.android.binder.ParcelUtils

abstract class LauncherOverlayInterfaceBinder : LauncherOverlayBinder(), ILauncherOverlay {

    init {
        attachInterface(this, INTERFACE_DESCRIPTOR)
    }

    @Throws(RemoteException::class)
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (super.onTransact(code, data, reply, flags)) return true

        when (code) {
            TRANSACTION_START_SCROLL -> startScroll()

            TRANSACTION_ON_SCROLL -> onScroll(data.readFloat())

            TRANSACTION_END_SCROLL -> endScroll()

            TRANSACTION_WINDOW_ATTACHED_LAYOUT -> {
                val params = ParcelUtils.readParcelable(data, LayoutParams.CREATOR)
                val callback = callbackFromBinder(data.readStrongBinder())
                val options = data.readInt()
                if (params != null && callback != null) windowAttached(params, callback, options)
            }

            TRANSACTION_REQUEST_VOICE_DETECTION -> requestVoiceDetection(ParcelUtils.readBoolean(data))

            TRANSACTION_ON_PAUSE -> onPause()

            TRANSACTION_ON_RESUME -> onResume()

            TRANSACTION_OPEN_OVERLAY -> openOverlay(data.readInt())

            TRANSACTION_WINDOW_DETACHED -> windowDetached(ParcelUtils.readBoolean(data))

            11 -> Unit // Voice search is always enabled, so we don't need to check the version

            12 -> Unit // Not used in the current implementation, kept for compatibility

            TRANSACTION_IS_ENABLED -> reply?.let {
                it.writeNoException()
                ParcelUtils.writeBoolean(it, true)
            }

            TRANSACTION_WINDOW_ATTACHED_BUNDLE -> {
                val bundle = ParcelUtils.readParcelable(data, Bundle.CREATOR)
                windowAttached(bundle, callbackFromBinder(data.readStrongBinder()))
            }

            TRANSACTION_CLOSE_OVERLAY -> closeOverlay(data.readInt())

            else -> return false
        }
        return true
    }

    private fun callbackFromBinder(binder: IBinder?): ILauncherOverlayCallback? {
        if (binder == null) return null
        val local = binder.queryLocalInterface(CALLBACK_INTERFACE_DESCRIPTOR)
        return local as? ILauncherOverlayCallback ?: LauncherOverlayCallback(binder)
    }

    private companion object {
        const val INTERFACE_DESCRIPTOR = "com.google.android.libraries.launcherclient.ILauncherOverlay"
        const val CALLBACK_INTERFACE_DESCRIPTOR =
            "com.google.android.libraries.launcherclient.ILauncherOverlayCallback"

        const val TRANSACTION_START_SCROLL = 1
        const val TRANSACTION_ON_SCROLL = 2
        const val TRANSACTION_END_SCROLL = 3
        const val TRANSACTION_WINDOW_ATTACHED_LAYOUT = 4
        const val TRANSACTION_REQUEST_VOICE_DETECTION = 5
        const val TRANSACTION_ON_PAUSE = 7
        const val TRANSACTION_ON_RESUME = 8
        const val TRANSACTION_OPEN_OVERLAY = 9
        const val TRANSACTION_WINDOW_DETACHED = 10
        const val TRANSACTION_IS_ENABLED = 13
        const val TRANSACTION_WINDOW_ATTACHED_BUNDLE = 14
        const val TRANSACTION_CLOSE_OVERLAY = 16
    }
}
