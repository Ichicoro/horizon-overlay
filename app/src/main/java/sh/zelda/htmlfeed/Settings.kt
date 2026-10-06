package sh.zelda.htmlfeed

import android.content.Context
import androidx.core.content.edit

/** The one thing worth remembering between runs: which page the -1 screen shows. */
object Settings {
    const val DEFAULT_URL = "https://google.com"

    private const val PREFS = "htmlfeed"
    private const val KEY_URL = "page_url"

    fun pageUrl(context: Context): String =
        prefs(context).getString(KEY_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_URL

    fun setPageUrl(context: Context, url: String) {
        prefs(context).edit { putString(KEY_URL, url) }
    }

    /** Accepts what people actually type: "example.com" becomes "https://example.com". */
    fun normalize(input: String): String {
        val url = input.trim()
        if (url.isEmpty()) return DEFAULT_URL
        return if (url.contains("://")) url else "https://$url"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
