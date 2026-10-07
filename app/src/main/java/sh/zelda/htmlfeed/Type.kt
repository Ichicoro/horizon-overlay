package sh.zelda.htmlfeed

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Google Sans Flex, taken from the device rather than bundled.
 *
 * Pixels register it as the named family `google-sans-flex` (see
 * `/product/etc/fonts_customization.xml`), which is what [DeviceFontFamilyName] looks up. It's a
 * variable font, so every weight below comes out of the one file. Anywhere the family isn't
 * installed the lookup quietly resolves to the system default, which is the right outcome -
 * there's nothing to bundle and nothing to license.
 */
val GoogleSansFlex = FontFamily(
    Font(DeviceFontFamilyName(FAMILY), weight = FontWeight.Light),
    Font(DeviceFontFamilyName(FAMILY), weight = FontWeight.Normal),
    Font(DeviceFontFamilyName(FAMILY), weight = FontWeight.Medium),
    Font(DeviceFontFamilyName(FAMILY), weight = FontWeight.SemiBold),
    Font(DeviceFontFamilyName(FAMILY), weight = FontWeight.Bold),
)

private const val FAMILY = "google-sans-flex"

/** The Material 3 scale, as-is, in [GoogleSansFlex]. */
val HubTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = GoogleSansFlex),
        displayMedium = displayMedium.copy(fontFamily = GoogleSansFlex),
        displaySmall = displaySmall.copy(fontFamily = GoogleSansFlex),
        headlineLarge = headlineLarge.copy(fontFamily = GoogleSansFlex),
        headlineMedium = headlineMedium.copy(fontFamily = GoogleSansFlex),
        headlineSmall = headlineSmall.copy(fontFamily = GoogleSansFlex),
        titleLarge = titleLarge.copy(fontFamily = GoogleSansFlex),
        titleMedium = titleMedium.copy(fontFamily = GoogleSansFlex),
        titleSmall = titleSmall.copy(fontFamily = GoogleSansFlex),
        bodyLarge = bodyLarge.copy(fontFamily = GoogleSansFlex),
        bodyMedium = bodyMedium.copy(fontFamily = GoogleSansFlex),
        bodySmall = bodySmall.copy(fontFamily = GoogleSansFlex),
        labelLarge = labelLarge.copy(fontFamily = GoogleSansFlex),
        labelMedium = labelMedium.copy(fontFamily = GoogleSansFlex),
        labelSmall = labelSmall.copy(fontFamily = GoogleSansFlex),
    )
}
