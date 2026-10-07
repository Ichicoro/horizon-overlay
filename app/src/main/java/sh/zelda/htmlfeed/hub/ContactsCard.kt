package sh.zelda.htmlfeed.hub

import android.content.Context
import android.graphics.BitmapFactory
import android.provider.ContactsContract
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Favorites as a row of faces.
 *
 * A tap opens the contact, where calling and messaging already live one button away; a long press
 * goes straight to the dialer for people who just want to call.
 */
@Composable
fun ContactsCard(limit: Int, refreshKey: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val actions = LocalHubActions.current
    val granted = ContactsRepository.hasPermission(context)
    val contacts by produceState(initialValue = emptyList<QuickContact>(), granted, limit, refreshKey) {
        value = if (granted) ContactsRepository.favorites(context, limit) else emptyList()
    }

    HubCard(title = "Contacts", modifier = modifier) {
        when {
            !granted -> HubPlaceholder(
                message = "Allow access to contacts in HTML Feed's settings to see your favorites here.",
                actionLabel = "Open settings",
                onAction = { actions.launch(hubSettingsIntent(context)) },
            )

            contacts.isEmpty() -> HubPlaceholder(
                message = "No favorites yet. Star someone in Contacts and they'll show up here.",
                actionLabel = "Open Contacts",
                onAction = { actions.launch(ContactsRepository.pickFavoritesIntent) },
            )

            else -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(contacts, key = { it.contactId }) { contact ->
                    ContactChip(
                        contact = contact,
                        onOpen = { actions.launch(contact.viewIntent) },
                        onDial = { contact.dialIntent?.let(actions::launch) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactChip(
    contact: QuickContact,
    onOpen: () -> Unit,
    onDial: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onOpen, onLongClick = onDial)
            .padding(vertical = 4.dp),
    ) {
        ContactAvatar(contact)
        Text(
            // Just the first name: a 64dp column has room for one word.
            text = contact.name.substringBefore(' '),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ContactAvatar(contact: QuickContact) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(initialValue = null, contact.contactId) {
        value = loadThumbnail(context, contact)
    }
    val fallbackColor = avatarColor(contact.name)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (photo == null) fallbackColor else Color.Transparent),
    ) {
        photo?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = contact.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp),
            )
        } ?: Text(
            text = contact.initials,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

private suspend fun loadThumbnail(context: Context, contact: QuickContact): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            ContactsContract.Contacts.openContactPhotoInputStream(
                context.contentResolver,
                contact.contentUri,
                /* preferHighres = */ false,
            )?.use { stream -> BitmapFactory.decodeStream(stream)?.asImageBitmap() }
        }.getOrNull()
    }

/** A stable color per contact, so the same person keeps the same circle. */
private fun avatarColor(name: String): Color {
    val palette = listOf(
        Color(0xFF7E57C2), Color(0xFF42A5F5), Color(0xFF26A69A),
        Color(0xFF66BB6A), Color(0xFFEF6C00), Color(0xFFEC407A),
        Color(0xFF8D6E63), Color(0xFF5C6BC0),
    )
    val index = (name.hashCode().toLong() and 0xFFFFFFFFL) % palette.size
    return palette[index.toInt()]
}
