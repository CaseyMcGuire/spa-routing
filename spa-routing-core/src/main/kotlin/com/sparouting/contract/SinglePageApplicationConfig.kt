package com.sparouting.contract

/** Application metadata, access handlers, and HTML rendering supplied by a generated config class. */
interface SinglePageApplicationConfig {
  val id: String
  val name: String
  val bundleName: String
  val routes: List<RouteManifest>
  val applicationAccessHandler: ApplicationAccessHandler<*>
  val routeAccessHandlers: RouteAccessHandlers<*>
  val htmlRenderer: HtmlRenderer
}
