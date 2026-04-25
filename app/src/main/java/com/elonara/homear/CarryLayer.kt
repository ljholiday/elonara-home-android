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

    init {
        closeControl.setOnClickListener {
            close()
        }
    }

    fun show(appId: CarryAppId) {
        val panel = panels.panelFor(appId)
        activeAppId = appId
        titleView.text = panel.title
        clearContent()
        contentContainer.addView(panel.createView(contentContainer.context))
        root.visibility = View.VISIBLE
        root.bringToFront()
    }

    fun close() {
        activeAppId = null
        clearContent()
        root.visibility = View.GONE
    }

    private fun clearContent() {
        for (index in 0 until contentContainer.childCount) {
            (contentContainer.getChildAt(index) as? WebView)?.destroy()
        }
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
}

class BrowserPanel : PlaceholderCarryPanel(
    appId = CarryAppId.BROWSER,
    title = "Browser",
    placeholderText = "Browser placeholder content"
)

class SocialPanel : CarryAppPanel {
    override val appId = CarryAppId.SOCIAL
    override val title = "Social"

    override fun createView(context: Context): View =
        WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                420.dp(context)
            )
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            loadUrl(SOCIAL_URL)
        }

    private fun Int.dp(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()

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
