package sh.zelda.htmlfeed

/**
 * The things the -1 screen can show. One entry here is one card in the overlay and one row in
 * settings; [id] is what gets persisted, so it outlives renames of the enum constant.
 */
enum class HubModule(
    val id: String,
    val label: String,
    val summary: String,
    /** Pinned modules are held at the top of the panel and can't be moved out of it. */
    val pinned: Boolean = false,
) {
    CLOCK("clock", "Clock", "Time, date, and a greeting", pinned = true),
    CONTACTS("contacts", "Contacts", "Favorites, tap to open or call"),
    WEATHER("weather", "Weather", "Now and the next few hours"),
    AGENDA("agenda", "Agenda", "What's coming up on your calendar"),
    WEB("web", "Web page", "Any URL, in a WebView");

    companion object {
        fun byId(id: String): HubModule? = entries.firstOrNull { it.id == id }
    }
}

/** A module plus where the user put it and whether they want it. */
data class HubModuleState(
    val module: HubModule,
    val enabled: Boolean,
)
