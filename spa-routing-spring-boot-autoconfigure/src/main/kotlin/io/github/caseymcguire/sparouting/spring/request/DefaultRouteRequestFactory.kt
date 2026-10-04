package io.github.caseymcguire.sparouting.spring.request

import com.sparouting.contract.RouteDefinition
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import org.springframework.web.servlet.function.ServerRequest

class DefaultRouteRequestFactory : RouteRequestFactory {
  override fun create(
    serverRequest: ServerRequest,
    application: SinglePageApplicationConfig,
    route: RouteDefinition
  ): RouteRequest {
    return RouteRequest(
      applicationId = application.applicationId,
      routeId = route.id,
      method = serverRequest.method().name(),
      path = serverRequest.path(),
      pathParameters = serverRequest.pathVariables(),
      queryString = serverRequest.params().mapValues { (_, values) -> values.toList() },
      headers = serverRequest.toRouteHeaders()
    )
  }
}
