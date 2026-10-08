package sh.zelda.htmlfeed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import sh.zelda.htmlfeed.hub.LocationRepository
import sh.zelda.htmlfeed.widget.HubWidgets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HtmlFeedTheme {
                SettingsScreen()
            }
        }
    }

    /**
     * The one moment this app is reliably allowed a location read: it's on screen, so the
     * foreground-only grant holds. The fix is stored, and that's what the panel forecasts for
     * when it's refused one of its own - see [LocationRepository.current].
     */
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { LocationRepository.current(this@MainActivity) }
        // Whatever sent someone here - a permission to grant, a favorite just starred, an option
        // to change - the widgets are a step behind by the time they're looking at this screen.
        HubWidgets.refresh(this)
    }
}
