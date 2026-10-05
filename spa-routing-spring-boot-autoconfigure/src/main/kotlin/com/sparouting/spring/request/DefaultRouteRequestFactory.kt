package com.sparouting.spring.request

import com.sparouting.contract.RouteManifest
import com.sparouting.runtime.config.SinglePageApplicationConfig
import com.sparouting.runtime.request.RouteRequest
import org.springframework.web.servlet.function.ServerRequest

class DefaultRouteRequestFactory : RouteRequestFactory {
  override fun create(
    serverRequest: ServerRequest,
    application: SinglePageApplicationConfig,
    route: RouteManifest
  ): RouteRequest {
    return RouteRequest(
      applicationId = application.manifest.id,
      routeId = route.id,
      method = serverRequest.method().name(),
      path = serverRequest.path(),
      pathParameters = serverRequest.pathVariables(),
      queryString = serverRequest.params().mapValues { (_, values) -> values.toList() },
      headers = serverRequest.toRouteHeaders()
    )
  }
}
