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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Shape
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
fun ContactsCard(
    limit: Int,
    refreshKey: Int,
    modifier: Modifier = Modifier,
    shape: Shape = SegmentShapes.single,
) {
    val context = LocalContext.current
    val actions = LocalHubActions.current
    val granted = ContactsRepository.hasPermission(context)
    val contacts by produceState(initialValue = emptyList<QuickContact>(), granted, limit, refreshKey) {
        value = if (granted) ContactsRepository.favorites(context, limit) else emptyList()
    }

    HubCard(
        title = "Contacts",
        modifier = modifier,
        shape = shape,
        // Edge to edge: the row pads its own scroll area below, so a chip scrolls out past the
        // card's edge rather than being cut off at an invisible inset.
        contentPadding = PaddingValues(),
    ) {
        when {
            !granted -> HubPlaceholder(
                modifier = Modifier.padding(horizontal = 16.dp),
                message = "Allow access to contacts in HTML Feed's settings to see your favorites here.",
                actionLabel = "Open settings",
                onAction = { actions.launch(hubSettingsIntent(context)) },
            )

            contacts.isEmpty() -> HubPlaceholder(
                modifier = Modifier.padding(horizontal = 16.dp),
                message = "No favorites yet. Star someone in Contacts and they'll show up here.",
                actionLabel = "Open Contacts",
                onAction = { actions.launch(ContactsRepository.pickFavoritesIntent) },
            )

            else -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
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
            .clip(MaterialTheme.shapes.large)
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
    // Keyed on the edit timestamp as well as the id: the row this sits in is keyed by contact
    // id, so without that second key the chip - and the bitmap it's holding - would outlive
    // every refresh and the picture would be decoded once and never again.
    val photo by produceState<ImageBitmap?>(null, contact.contactId, contact.lastUpdated) {
        value = loadThumbnail(context, contact)
    }
    val (container, onContainer) = avatarColors(contact.name)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (photo == null) container else Color.Transparent),
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
            color = onContainer,
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

/**
 * A stable container role per contact, so the same person keeps the same circle and the circles
 * stay in the wallpaper's palette rather than a set of colors picked here.
 */
@Composable
internal fun avatarColors(name: String): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )
    val index = (name.hashCode().toLong() and 0xFFFFFFFFL) % palette.size
    return palette[index.toInt()]
}
