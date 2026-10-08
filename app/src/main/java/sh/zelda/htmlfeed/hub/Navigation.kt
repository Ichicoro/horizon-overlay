package sh.zelda.htmlfeed.hub

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Somewhere a contact lives, as one tappable destination.
 *
 * A contact with both a home and a work address gives two of these, which is the point: picking
 * between them is the whole job of the card.
 */
data class NavDestination(
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    /** "Home", "Work", or whatever the user named it. */
    val label: String,
    val address: String,
) {
    /**
     * What settings stores when only some destinations are wanted.
     *
     * Keyed on the lookup key and the label rather than the row id, because a sync can renumber
     * rows; the cost is that renaming a custom label loses that one selection.
     */
    val key: String get() = "$lookupKey#$label"

    val contentUri: Uri
        get() = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)

    val initials: String
        get() = name.split(' ', '-', '.')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }

    /** One line, for a card that has one line to give it. */
    val shortAddress: String get() = address.lineSequence().joinToString(", ") { it.trim() }

    /**
     * Turn-by-turn where something can do it, the generic map pin where nothing can.
     *
     * `google.navigation:` starts directions immediately, which is what this card is for, but
     * it's Google Maps' own scheme; `geo:` is the one every maps app handles. Both are declared
     * in `<queries>`, without which this check would always come back empty on Android 11+.
     */
    fun navigationIntent(context: Context): Intent {
        val query = Uri.encode(shortAddress)
        val directions = Intent(Intent.ACTION_VIEW, "google.navigation:q=$query".toUri())
        return if (directions.resolveActivity(context.packageManager) != null) {
            directions
        } else {
            Intent(Intent.ACTION_VIEW, "geo:0,0?q=$query".toUri())
        }
    }
}

object NavigationRepository {
    /** Reads contacts, same as the favorites row. */
    const val PERMISSION = ContactsRepository.PERMISSION

    fun hasPermission(context: Context): Boolean = ContactsRepository.hasPermission(context)

    /** Every address on every contact, by name then label. */
    suspend fun destinations(context: Context): List<NavDestination> =
        withContext(Dispatchers.IO) {
            if (!hasPermission(context)) return@withContext emptyList()
            val projection = arrayOf(
                StructuredPostal.CONTACT_ID,
                ContactsContract.Data.LOOKUP_KEY,
                ContactsContract.Data.DISPLAY_NAME_PRIMARY,
                StructuredPostal.FORMATTED_ADDRESS,
                StructuredPostal.TYPE,
                StructuredPostal.LABEL,
            )
            val destinations = mutableListOf<NavDestination>()
            context.contentResolver.query(
                StructuredPostal.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.Data.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    val address = cursor.getString(3)?.takeIf { it.isNotBlank() } ?: continue
                    destinations += NavDestination(
                        contactId = cursor.getLong(0),
                        lookupKey = cursor.getString(1).orEmpty(),
                        name = name,
                        label = StructuredPostal.getTypeLabel(
                            context.resources,
                            cursor.getInt(4),
                            cursor.getString(5),
                        ).toString(),
                        address = address,
                    )
                }
            }
            destinations
        }

    /**
     * What the card shows: everything when [selection] is null, otherwise just what was picked.
     *
     * Null rather than "empty means everything" so that unticking the last box leaves an empty
     * card - which is recoverable - instead of silently showing all of them back.
     */
    fun filter(
        destinations: List<NavDestination>,
        selection: Set<String>?,
    ): List<NavDestination> =
        if (selection == null) destinations else destinations.filter { it.key in selection }
}
