package sh.zelda.htmlfeed.hub

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One starred contact, reduced to what a card needs. */
data class QuickContact(
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    val phoneNumber: String?,
    /**
     * When the provider last saw this contact change, which is what the photo is keyed on.
     *
     * A contact id alone can't say that the person's picture is new, and the card holds its
     * decoded thumbnail for as long as the panel lives - this is the part that moves when
     * someone edits a contact.
     */
    val lastUpdated: Long,
) {
    /** The contact row itself; the photo stream and the lookup URI both start here. */
    val contentUri: Uri
        get() = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)

    val initials: String
        get() = name.split(' ', '-', '.')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }

    val viewIntent: Intent
        get() = Intent(
            Intent.ACTION_VIEW,
            ContactsContract.Contacts.getLookupUri(contactId, lookupKey),
        )

    /** ACTION_DIAL rather than ACTION_CALL: it hands off to the dialer, so no CALL_PHONE. */
    val dialIntent: Intent?
        get() = phoneNumber?.let { Intent(Intent.ACTION_DIAL, "tel:$it".toUri()) }
}

object ContactsRepository {
    const val PERMISSION = Manifest.permission.READ_CONTACTS

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * Starred contacts, newest-primary number first.
     *
     * Reads the phone table rather than the contact table so a number comes back in the same
     * pass; a favorite with no number still shows up, just without the call button.
     */
    suspend fun favorites(context: Context, limit: Int): List<QuickContact> =
        withContext(Dispatchers.IO) {
            if (!hasPermission(context)) return@withContext emptyList()
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP,
            )
            val contacts = mutableListOf<QuickContact>()
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                "${ContactsContract.Contacts.STARRED} = 1",
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { cursor ->
                while (cursor.moveToNext() && contacts.size < limit) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    contacts += QuickContact(
                        contactId = id,
                        lookupKey = cursor.getString(1).orEmpty(),
                        name = name,
                        phoneNumber = if (cursor.getInt(3) > 0) primaryNumber(context, id) else null,
                        lastUpdated = cursor.getLong(4),
                    )
                }
            }
            contacts
        }

    private fun primaryNumber(context: Context, contactId: Long): String? =
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            // A number the user marked as the default comes first.
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC",
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

    /** For the empty state: send people to the contacts app to star someone. */
    val pickFavoritesIntent: Intent
        get() = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
}
