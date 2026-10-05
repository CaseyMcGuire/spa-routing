package com.sparouting.runtime.rendering

import com.sparouting.contract.SinglePageApplicationConfig

/** Builds the default HTML shell without depending on an HTTP framework. */
class HtmlDocumentRenderer(
  private val options: HtmlRenderingOptions = HtmlRenderingOptions()
) {
  /** Build the HTML document; adapters choose how to write it to their response. */
  fun render(application: SinglePageApplicationConfig): String {
    val bundleBasePath = options.bundleBasePath.trimEnd('/')
    val routeStylesheet = "$bundleBasePath/${application.bundleName}.css"
    val bundleScript = "$bundleBasePath/${application.bundleName}.bundle.js"

    return buildString {
      appendLine("<!doctype html>")
      appendLine("<html lang=\"en\">")
      appendLine("<head>")
      appendLine("  <meta charset=\"utf-8\">")
      appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
      appendLine("  <title>${application.name.escapeHtml()}</title>")
      options.globalStylesheet?.let { stylesheet ->
        appendLine("  <link rel=\"stylesheet\" href=\"${stylesheet.escapeHtmlAttribute()}\">")
      }
      if (options.includeRouteStylesheet) {
        appendLine("  <link rel=\"stylesheet\" href=\"${routeStylesheet.escapeHtmlAttribute()}\">")
      }
      appendLine("</head>")
      appendLine("<body>")
      appendLine("  <div id=\"root\"></div>")
      appendLine("  <script type=\"module\" src=\"${bundleScript.escapeHtmlAttribute()}\"></script>")
      appendLine("</body>")
      appendLine("</html>")
    }
  }

  private fun String.escapeHtml(): String {
    return replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
  }

  private fun String.escapeHtmlAttribute(): String {
    return escapeHtml()
      .replace("\"", "&quot;")
  }
}
