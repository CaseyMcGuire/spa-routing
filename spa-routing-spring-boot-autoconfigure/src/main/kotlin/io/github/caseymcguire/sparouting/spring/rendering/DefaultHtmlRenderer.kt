package io.github.caseymcguire.sparouting.spring.rendering

import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.rendering.HtmlDocumentRenderer
import io.github.caseymcguire.sparouting.runtime.rendering.HtmlRenderingOptions
import io.github.caseymcguire.sparouting.spring.autoconfigure.RoutingProperties
import org.springframework.http.MediaType
import org.springframework.web.servlet.function.ServerResponse

/** Writes the framework-neutral HTML document as a Spring MVC response. */
class DefaultHtmlRenderer(
  private val properties: RoutingProperties
) : HtmlRenderer {
  override fun render(application: SinglePageApplicationConfig): ServerResponse {
    val renderer = HtmlDocumentRenderer(HtmlRenderingOptions(
      bundleBasePath = properties.assets.bundleBasePath,
      includeRouteStylesheet = properties.assets.includeRouteStylesheet,
      globalStylesheet = properties.assets.globalStylesheet
    ))
    return ServerResponse.ok()
      .contentType(MediaType.TEXT_HTML)
      .body(renderer.render(application))
  }
}
