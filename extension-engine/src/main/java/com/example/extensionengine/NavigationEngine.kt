package com.example.extensionengine

/**
 * Interface that tracks the active page load state, traverses back/forward histories,
 * handles URLs, and evaluates dynamic scripts inside the document scope.
 */
interface NavigationEngine {
    /**
     * Instructs the tab renderer to navigate to the specified URI destination.
     */
    fun loadUrl(tabId: String, url: String)

    /**
     * Determines whether the renderer has histories to go back.
     */
    fun canGoBack(tabId: String): Boolean

    /**
     * Traverses to the previous entry in the local document backstack.
     */
    fun goBack(tabId: String)

    /**
     * Determines whether the renderer has histories to go forward.
     */
    fun canGoForward(tabId: String): Boolean

    /**
     * Traverses to the next entry in the local document backstack.
     */
    fun goForward(tabId: String)

    /**
     * Forces the engine to request the remote webpage context again.
     */
    fun reload(tabId: String)

    /**
     * Cancels any active network transfers for this tab.
     */
    fun stopLoading(tabId: String)

    /**
     * Evaluates custom Javascript directly in the page window context.
     */
    fun evaluateJavascript(tabId: String, code: String, callback: ((String?) -> Unit)?)

    /**
     * Configures the browser user agent profile for desktop or mobile compatibility.
     */
    fun setUserAgentString(tabId: String, userAgent: String)

    /**
     * Retrieves the current user agent configuration for the tab context.
     */
    fun getUserAgentString(tabId: String): String

    /**
     * Registers a listener to monitor URL changes, load progression, and load failures.
     */
    fun registerNavigationListener(listener: NavigationEngineListener)

    /**
     * Unregisters a previously attached navigation listener.
     */
    fun unregisterNavigationListener(listener: NavigationEngineListener)
}

/**
 * Navigation state callback interfaces to maintain event propagation independent of WebView.
 */
interface NavigationEngineListener {
    fun onPageStarted(tabId: String, url: String)
    fun onPageFinished(tabId: String, url: String)
    fun onProgressChanged(tabId: String, progress: Int)
    fun onReceivedTitle(tabId: String, title: String)
    fun onReceivedFavicon(tabId: String, faviconUrl: String?)
    fun shouldOverrideUrlLoading(tabId: String, url: String): Boolean
}
