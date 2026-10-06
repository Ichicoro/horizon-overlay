package com.google.android.binder

import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException
import java.util.logging.Logger

open class BinderInterface(
    private val binder: IBinder,
    private val interfaceToken: String,
) : IInterface {

    override fun asBinder(): IBinder = binder

    protected fun createParcel(): Parcel = Parcel.obtain().apply {
        writeInterfaceToken(interfaceToken)
    }

    /**
     * Performs a two-way transaction with the given transaction code and input Parcel.
     *
     * @return the reply Parcel.
     * @throws RemoteException if the IPC transaction fails.
     */
    @Throws(RemoteException::class)
    fun transactWithReply(transactionCode: Int, inputParcel: Parcel): Parcel {
        val replyParcel = Parcel.obtain()
        try {
            binder.transact(transactionCode, inputParcel, replyParcel, 0)
            replyParcel.readException() // Throws RemoteException if the transaction failed
            return replyParcel
        } catch (e: Throwable) {
            if (e !is RemoteException && e !is RuntimeException) throw e
            LOGGER.warning("Transaction failed for code $transactionCode: ${e.message}")
            replyParcel.recycle()
            throw e // Propagate the exception to the caller
        } finally {
            inputParcel.recycle() // Always recycle the input Parcel
        }
    }

    /**
     * Performs a one-way transaction with the given transaction code and input Parcel.
     * No reply is expected.
     *
     * @throws RemoteException if the IPC transaction fails.
     */
    @Throws(RemoteException::class)
    protected fun transactOneWay(transactionCode: Int, inputParcel: Parcel) {
        try {
            binder.transact(transactionCode, inputParcel, null, 1)
        } catch (e: RemoteException) {
            LOGGER.warning("One-way transaction failed for code $transactionCode: ${e.message}")
            throw e // Propagate the exception to the caller
        } finally {
            inputParcel.recycle() // Always recycle the input Parcel
        }
    }

    private companion object {
        val LOGGER: Logger = Logger.getLogger(BinderInterface::class.java.name)
    }
}
