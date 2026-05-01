package com.elonn.worldar

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
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
    socialPanelHost: SocialPanelHost = NoOpSocialPanelHost,
    private val panels: List<CarryAppPanel> = listOf(
        BrowserPanel(),
        SocialPanel(socialPanelHost),
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

interface SocialPanelHost {
    fun openFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams
    ): Boolean
}

object NoOpSocialPanelHost : SocialPanelHost {
    override fun openFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams
    ): Boolean {
        filePathCallback.onReceiveValue(null)
        return true
    }
}

class BrowserPanel : PlaceholderCarryPanel(
    appId = CarryAppId.BROWSER,
    title = "Browser",
    placeholderText = "Browser placeholder content"
)

class SocialPanel(private val host: SocialPanelHost) : CarryAppPanel {
    override val appId = CarryAppId.SOCIAL
    override val title = "Social"
    private var container: FrameLayout? = null
    private var statusView: TextView? = null
    private var webView: WebView? = null

    override fun createView(context: Context): View =
        existingOrNewContainer(context).also { view ->
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
        CookieManager.getInstance().flush()
        webView?.destroy()
        webView = null
        statusView = null
        container = null
    }

    private fun existingOrNewContainer(context: Context): FrameLayout =
        container ?: FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            val socialWebView = createWebView(context)
            val socialStatusView = createStatusView(context)
            addView(socialWebView)
            addView(socialStatusView)
            container = this
            webView = socialWebView
            statusView = socialStatusView
        }

    private fun createWebView(context: Context): WebView =
        WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            configureCookies(this)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    CookieManager.getInstance().flush()
                    statusView?.visibility = View.GONE
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        showStatus("Social could not load. Check connection and try again.")
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress < 100) {
                        showStatus("Loading Social...")
                    }
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams
                ): Boolean = host.openFileChooser(filePathCallback, fileChooserParams)
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            showStatus("Loading Social...")
            loadUrl(SOCIAL_URL)
        }

    private fun createStatusView(context: Context): TextView =
        TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            text = "Loading Social..."
            setTextColor(Color.parseColor("#D0DDD5"))
            textSize = 14.0f
            visibility = View.VISIBLE
        }

    private fun configureCookies(webView: WebView) {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
    }

    private fun showStatus(message: String) {
        statusView?.text = message
        statusView?.visibility = View.VISIBLE
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
