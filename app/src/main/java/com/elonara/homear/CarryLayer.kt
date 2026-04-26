package com.elonara.homear

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView

enum class CarryAppId {
    BROWSER,
    SOCIAL,
    CALENDAR,
    MESSAGES,
    SETTINGS
}

data class CarryDockButtonSpec(
    val viewId: Int,
    val appId: CarryAppId
)

class CarryAppDock(
    private val root: View,
    private val buttons: List<CarryDockButtonSpec>,
    private val onSelected: (CarryAppId) -> Unit
) {
    fun bind() {
        buttons.forEach { button ->
            root.findViewById<TextView>(button.viewId).setOnClickListener {
                onSelected(button.appId)
            }
        }
    }
}

class CarryActiveWindow(
    private val root: View,
    private val titleView: TextView,
    private val contentContainer: FrameLayout,
    closeControl: View,
    private val panels: CarryPanelRegistry
) {
    var activeAppId: CarryAppId? = null
        private set
    private var activePanel: CarryAppPanel? = null

    init {
        closeControl.setOnClickListener {
            close()
        }
    }

    fun show(appId: CarryAppId) {
        if (activeAppId == appId && root.visibility == View.VISIBLE) {
            return
        }

        val panel = panels.panelFor(appId)
        clearContent()
        activeAppId = appId
        activePanel = panel
        titleView.text = panel.title
        contentContainer.addView(panel.createView(contentContainer.context))
        root.visibility = View.VISIBLE
    }

    fun close() {
        activeAppId = null
        clearContent()
        root.visibility = View.INVISIBLE
    }

    fun handleBack(): Boolean {
        if (root.visibility != View.VISIBLE) {
            return false
        }

        if (activePanel?.handleBack() == true) {
            return true
        }

        close()
        return true
    }

    private fun clearContent() {
        activePanel?.onRemovedFromWindow()
        activePanel = null
        contentContainer.removeAllViews()
    }
}

class CarryPanelRegistry(
    private val panels: List<CarryAppPanel> = listOf(
        BrowserPanel(),
        SocialPanel(),
        CalendarPanel(),
        MessagesPanel(),
        SettingsPanel()
    )
) {
    fun panelFor(appId: CarryAppId): CarryAppPanel =
        panels.first { it.appId == appId }
}

interface CarryAppPanel {
    val appId: CarryAppId
    val title: String

    fun createView(context: Context): View

    fun handleBack(): Boolean = false

    fun onRemovedFromWindow() = Unit
}

class BrowserPanel : PlaceholderCarryPanel(
    appId = CarryAppId.BROWSER,
    title = "Browser",
    placeholderText = "Browser placeholder content"
)

class SocialPanel : CarryAppPanel {
    override val appId = CarryAppId.SOCIAL
    override val title = "Social"
    private var webView: WebView? = null

    override fun createView(context: Context): View =
        existingOrNewWebView(context).also { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }

    override fun handleBack(): Boolean {
        val view = webView ?: return false
        if (!view.canGoBack()) {
            return false
        }

        view.goBack()
        return true
    }

    override fun onRemovedFromWindow() {
        webView?.destroy()
        webView = null
    }

    private fun existingOrNewWebView(context: Context): WebView =
        webView ?: WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            loadUrl(SOCIAL_URL)
            webView = this
        }

    private companion object {
        private const val SOCIAL_URL = "https://social.elonara.com"
    }
}

class CalendarPanel : PlaceholderCarryPanel(
    appId = CarryAppId.CALENDAR,
    title = "Calendar",
    placeholderText = "Calendar placeholder content"
)

class MessagesPanel : PlaceholderCarryPanel(
    appId = CarryAppId.MESSAGES,
    title = "Messages",
    placeholderText = "Messages placeholder content"
)

class SettingsPanel : PlaceholderCarryPanel(
    appId = CarryAppId.SETTINGS,
    title = "Settings",
    placeholderText = "Settings placeholder content"
)

abstract class PlaceholderCarryPanel(
    override val appId: CarryAppId,
    override val title: String,
    private val placeholderText: String
) : CarryAppPanel {
    override fun createView(context: Context): View =
        TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = placeholderText
            setTextColor(Color.parseColor("#D0DDD5"))
            textSize = 16.0f
        }
}
