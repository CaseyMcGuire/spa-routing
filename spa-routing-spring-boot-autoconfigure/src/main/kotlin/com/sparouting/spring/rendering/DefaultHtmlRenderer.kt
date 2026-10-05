package com.sparouting.spring.rendering

import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.rendering.HtmlDocumentRenderer
import com.sparouting.runtime.rendering.HtmlRenderingOptions
import com.sparouting.spring.autoconfigure.RoutingProperties
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
