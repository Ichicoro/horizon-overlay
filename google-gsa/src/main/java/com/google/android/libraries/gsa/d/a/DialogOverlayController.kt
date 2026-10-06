package com.google.android.libraries.gsa.d.a

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ActionMode
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.SearchEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.util.Collections

open class DialogOverlayController(
    context: Context,
    theme: Int,
    dialogTheme: Int,
) : ContextThemeWrapper(context, theme), Window.Callback, DialogListeners {

    var windowManager: WindowManager
    var window: Window
    var windowView: View? = null

    private val dialogs: MutableSet<DialogInterface> =
        Collections.synchronizedSet(mutableSetOf())
    private var onBackInvokedCallback: OnBackInvokedCallback? = null
    private var backCallbackRegistered = false

    init {
        val dialogWindow = Dialog(context, dialogTheme).window
            ?: throw IllegalStateException("Dialog window cannot be null")
        window = dialogWindow
        window.setCallback(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    open fun onBackPressed() = Unit

    open fun closePanelIfNeeded(flags: Int) = Unit

    internal fun registerBackCallbackIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (backCallbackRegistered) return
        val dispatcher = window.onBackInvokedDispatcher ?: return
        val callback = onBackInvokedCallback
            ?: OnBackInvokedCallback { onBackPressed() }.also { onBackInvokedCallback = it }
        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        backCallbackRegistered = true
    }

    internal fun unregisterBackCallbackIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val callback = onBackInvokedCallback
        if (!backCallbackRegistered || callback == null) return
        window.onBackInvokedDispatcher?.unregisterOnBackInvokedCallback(callback)
        backCallbackRegistered = false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                onBackPressed()
            }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_HOME) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                closePanelIfNeeded(1)
            }
            return true
        }
        return window.superDispatchKeyEvent(event)
    }

    override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean =
        window.superDispatchKeyShortcutEvent(event)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        window.superDispatchTouchEvent(event)

    override fun dispatchTrackballEvent(event: MotionEvent): Boolean =
        window.superDispatchTrackballEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        window.superDispatchGenericMotionEvent(event)

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean = false

    override fun onCreatePanelView(featureId: Int): View? = null

    override fun onCreatePanelMenu(featureId: Int, menu: Menu): Boolean = false

    override fun onPreparePanel(featureId: Int, view: View?, menu: Menu): Boolean = true

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean = true

    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean = false

    override fun onWindowAttributesChanged(params: WindowManager.LayoutParams) {
        windowView?.let { windowManager.updateViewLayout(it, params) }
    }

    override fun onContentChanged() = Unit

    override fun onWindowFocusChanged(hasFocus: Boolean) = Unit

    override fun onAttachedToWindow() = Unit

    override fun onDetachedFromWindow() = Unit

    override fun onPanelClosed(featureId: Int, menu: Menu) = Unit

    override fun onSearchRequested(): Boolean = false

    override fun onSearchRequested(searchEvent: SearchEvent?): Boolean = false

    override fun onWindowStartingActionMode(callback: ActionMode.Callback?): ActionMode? = null

    override fun onWindowStartingActionMode(
        callback: ActionMode.Callback?,
        type: Int,
    ): ActionMode? = null

    override fun onActionModeStarted(mode: ActionMode?) = Unit

    override fun onActionModeFinished(mode: ActionMode?) = Unit

    override fun startActivity(intent: Intent) {
        super.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun startActivity(intent: Intent, options: Bundle?) {
        super.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
    }

    override fun onShow(dialog: DialogInterface) {
        dialogs.add(dialog)
    }

    override fun onDismiss(dialog: DialogInterface) {
        dialogs.remove(dialog)
    }

    internal fun dismissAllDialogs() {
        synchronized(dialogs) {
            dialogs.toTypedArray().forEach { (it as? Dialog)?.dismiss() }
            dialogs.clear()
        }
    }
}
