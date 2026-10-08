package sh.zelda.htmlfeed.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.ContactsContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.zelda.htmlfeed.Settings
import sh.zelda.htmlfeed.hub.ContactsRepository
import sh.zelda.htmlfeed.hub.QuickContact

/**
 * Favorites as a grid of faces.
 *
 * The panel's card is a row that scrolls sideways; a widget can't scroll sideways, so this wraps
 * instead - as many faces per row as the width allows, and the rows themselves scroll.
 *
 * A tap opens the contact. The card's long press straight to the dialer has no equivalent here:
 * a widget gets one click per view and nothing else.
 */
class ContactsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val limit = Settings.snapshot(context).contactsLimit
        val granted = ContactsRepository.hasPermission(context)
        val faces = if (granted) {
            ContactsRepository.favorites(context, limit)
                .map { WidgetFace(it, thumbnail(context, it)) }
        } else {
            emptyList()
        }

        provideContent {
            GlanceTheme {
                HubWidgetSurface(title = "Contacts") {
                    when {
                        !granted -> WidgetPlaceholder(
                            "Allow access to contacts to see your favorites here.",
                        )

                        faces.isEmpty() -> WidgetPlaceholder(
                            "No favorites yet. Star someone in Contacts and they'll show up here.",
                        )

                        else -> FaceGrid(faces)
                    }
                }
            }
        }
    }
}

/** One favorite and the photo that goes with it, already decoded. */
private class WidgetFace(val contact: QuickContact, val photo: Bitmap?)

/**
 * Thumbnails rather than the high-res photo: everything a widget draws crosses a Binder
 * transaction as a RemoteViews, and that has a size limit a handful of full-size contact photos
 * would go straight through.
 */
private suspend fun thumbnail(context: Context, contact: QuickContact): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            ContactsContract.Contacts.openContactPhotoInputStream(
                context.contentResolver,
                contact.contentUri,
                /* preferHighres = */ false,
            )?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

/** 52dp of face plus the name under it; the gap that follows a column is part of the column. */
private val AVATAR = 52.dp
private val CHIP_WIDTH = 64.dp
private val CHIP_GAP = 8.dp

@Composable
private fun FaceGrid(faces: List<WidgetFace>) {
    // The widget's real width in dp, which SizeMode.Exact makes exact: how many faces fit is a
    // division, so a resize adds a column the moment there's room for one.
    val available = LocalSize.current.width - 32.dp
    val columns = (available / (CHIP_WIDTH + CHIP_GAP)).toInt().coerceIn(1, 8)

    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(faces.chunked(columns)) { row ->
            Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = CHIP_GAP)) {
                row.forEach { face ->
                    FaceChip(face)
                    Box(modifier = GlanceModifier.width(CHIP_GAP), content = {})
                }
            }
        }
    }
}

@Composable
private fun FaceChip(face: WidgetFace) {
    Column(
        modifier = GlanceModifier
            .width(CHIP_WIDTH)
            .clickable(actionStartActivity(face.contact.viewIntent)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FaceAvatar(face)
        Text(
            // Just the first name: a 64dp column has room for one word.
            text = face.contact.name.substringBefore(' '),
            style = WidgetText.secondary.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun FaceAvatar(face: WidgetFace) {
    val (container, onContainer) = widgetAvatarColors(face.contact.name)
    // A circle is an outline clip, which is an API 31 feature; below that these come out as
    // rounded squares, on the same releases that have no dynamic colour either.
    val circle = GlanceModifier.size(AVATAR).cornerRadius(AVATAR / 2)

    if (face.photo != null) {
        Image(
            provider = ImageProvider(face.photo),
            contentDescription = face.contact.name,
            contentScale = ContentScale.Crop,
            modifier = circle,
        )
    } else {
        Box(modifier = circle.background(container), contentAlignment = Alignment.Center) {
            Text(
                text = face.contact.initials,
                style = WidgetText.primary.copy(color = onContainer, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}
