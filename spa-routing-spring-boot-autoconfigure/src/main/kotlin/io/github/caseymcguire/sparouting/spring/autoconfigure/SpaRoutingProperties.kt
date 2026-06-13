package io.github.caseymcguire.sparouting.spring.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("spa-routing")
class SpaRoutingProperties {
  val server = Server()
  val routeDecision = RouteDecision()
  val assets = Assets()

  class Server {
    var enabled: Boolean = true
    var invalidPathParameterStatus: Int = 400
  }

  class RouteDecision {
    var enabled: Boolean = true
    var path: String = "/__spa/route-decision"
  }

  class Assets {
    var bundleBasePath: String = "/bundles"
    var includeRouteStylesheet: Boolean = true
    var globalStylesheet: String? = "/bundles/stylex.css"
  }
}
