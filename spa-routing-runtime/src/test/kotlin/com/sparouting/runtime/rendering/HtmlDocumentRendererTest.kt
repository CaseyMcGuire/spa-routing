package com.sparouting.runtime.rendering

import com.sparouting.contract.RouteManifest
import com.sparouting.runtime.testsupport.TestSinglePageApplicationConfig
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class HtmlDocumentRendererTest {
  private val config = TestSinglePageApplicationConfig(
    name = "News & <updates>",
    routes = listOf(RouteManifest("/test", "Index")),
    bundleName = "custom\"bundle"
  )

  @Test
  fun `default document uses escaped config metadata and includes stylesheets`() {
    val html = HtmlDocumentRenderer().render(config)

    assertContains(html, "<title>News &amp; &lt;updates&gt;</title>")
    assertContains(html, "<div id=\"root\"></div>")
    assertContains(html, "href=\"/bundles/stylex.css\"")
    assertContains(html, "href=\"/bundles/custom&quot;bundle.css\"")
    assertContains(html, "src=\"/bundles/custom&quot;bundle.bundle.js\"")
  }

  @Test
  fun `asset options change bundle paths and can omit stylesheets`() {
    val html = HtmlDocumentRenderer(
      bundleBasePath = "/assets/",
      includeRouteStylesheet = false,
      globalStylesheet = null
    ).render(config)

    assertContains(html, "src=\"/assets/custom&quot;bundle.bundle.js\"")
    assertFalse(html.contains("rel=\"stylesheet\""))
  }
}
