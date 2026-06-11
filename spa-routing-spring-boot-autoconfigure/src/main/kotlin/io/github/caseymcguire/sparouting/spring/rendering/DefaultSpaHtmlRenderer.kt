package io.github.caseymcguire.sparouting.spring.rendering

import io.github.caseymcguire.sparouting.spring.autoconfigure.SpaRoutingProperties
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import org.springframework.http.MediaType
import org.springframework.web.servlet.function.ServerResponse

class DefaultSpaHtmlRenderer(
  private val properties: SpaRoutingProperties
) : SpaHtmlRenderer {
  override fun render(application: SinglePageApplicationConfig): ServerResponse {
    return ServerResponse.ok()
      .contentType(MediaType.TEXT_HTML)
      .body(application.toHtml())
  }

  private fun SinglePageApplicationConfig.toHtml(): String {
    val bundleBasePath = properties.assets.bundleBasePath.trimEnd('/')
    val routeStylesheet = "$bundleBasePath/${bundleName}.css"
    val bundleScript = "$bundleBasePath/${bundleName}.bundle.js"

    return buildString {
      appendLine("<!doctype html>")
      appendLine("<html lang=\"en\">")
      appendLine("<head>")
      appendLine("  <meta charset=\"utf-8\">")
      appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
      appendLine("  <title>${name.escapeHtml()}</title>")
      properties.assets.globalStylesheet?.let { stylesheet ->
        appendLine("  <link rel=\"stylesheet\" href=\"${stylesheet.escapeHtmlAttribute()}\">")
      }
      if (properties.assets.includeRouteStylesheet) {
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
