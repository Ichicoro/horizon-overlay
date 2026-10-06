package com.google.android.binder

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

open class LauncherOverlayBinder : Binder(), IInterface {

    override fun asBinder(): IBinder = this

    /**
     * Handles incoming IPC transactions.
     *
     * @return true if the transaction was handled, false otherwise.
     * @throws RemoteException if an error occurs during transaction processing.
     */
    @Throws(RemoteException::class)
    public override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        // Handle high transaction codes by delegating to the superclass
        if (code > 16777215) {
            return super.onTransact(code, data, reply, flags)
        }

        // Enforce interface descriptor to ensure correct client
        try {
            data.enforceInterface(interfaceDescriptor!!)
            return false
        } catch (e: SecurityException) {
            throw RemoteException("Interface descriptor mismatch: ${e.message}")
        }
    }
}
