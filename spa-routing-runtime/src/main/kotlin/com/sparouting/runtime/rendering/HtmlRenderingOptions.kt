package com.sparouting.runtime.rendering

/** Asset URLs and stylesheet selection for the default HTML document. */
data class HtmlRenderingOptions(
  val bundleBasePath: String = "/bundles",
  val includeRouteStylesheet: Boolean = true,
  val globalStylesheet: String? = "/bundles/stylex.css"
)
