package com.sparouting.spring.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("spa-routing")
class RoutingProperties {
  val server = Server()
  val routeDecision = RouteDecision()

  class Server {
    var enabled: Boolean = true
    var invalidPathParameterStatus: Int = 400
    var invalidQueryStringStatus: Int = 400
  }

  class RouteDecision {
    var enabled: Boolean = true
    var path: String = "/__spa/route-decision"
  }
}
