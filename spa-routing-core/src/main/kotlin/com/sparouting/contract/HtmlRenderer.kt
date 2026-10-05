package com.sparouting.contract

/** Builds an application's HTML document after access is allowed. Adapters write the HTTP response. */
fun interface HtmlRenderer {
  fun render(application: SinglePageApplicationConfig): String
}
