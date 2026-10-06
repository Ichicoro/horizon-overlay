package com.google.android.binder

import android.os.IInterface
import android.os.Parcel
import android.os.Parcelable

object ParcelUtils {

    /** Reads a boolean from [parcel]: true when the parcel holds 1. */
    fun readBoolean(parcel: Parcel): Boolean = parcel.readInt() == 1

    fun writeBoolean(parcel: Parcel, value: Boolean) {
        parcel.writeInt(if (value) 1 else 0)
    }

    /** Reads a [Parcelable] with [creator], or null when the parcel holds no object. */
    fun <T : Parcelable> readParcelable(parcel: Parcel, creator: Parcelable.Creator<T>): T? =
        if (parcel.readInt() == 0) null else creator.createFromParcel(parcel)

    fun writeParcelable(parcel: Parcel, parcelable: Parcelable?) {
        if (parcelable == null) {
            parcel.writeInt(0)
            return
        }
        parcel.writeInt(1)
        parcelable.writeToParcel(parcel, 0)
    }

    fun writeInterface(parcel: Parcel, iInterface: IInterface?) {
        parcel.writeStrongBinder(iInterface?.asBinder())
    }
}
