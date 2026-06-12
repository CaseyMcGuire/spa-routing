package io.github.caseymcguire.sparouting.spring.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("spa-routing")
class SpaRoutingProperties {
  val server = Server()
  val assets = Assets()

  class Server {
    var enabled: Boolean = true
    var invalidPathParameterStatus: Int = 400
    var routeCheckHeader: String = "X-Spa-Route-Check"
  }

  class Assets {
    var bundleBasePath: String = "/bundles"
    var includeRouteStylesheet: Boolean = true
    var globalStylesheet: String? = "/bundles/stylex.css"
  }
}
