package io.github.caseymcguire.sparouting.spring.request

import com.caseymcguiredotcom.sparoutecontract.SpaRouteDefinition
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import org.springframework.web.servlet.function.ServerRequest

class DefaultSpaRouteRequestFactory : SpaRouteRequestFactory {
  override fun create(
    serverRequest: ServerRequest,
    application: SinglePageApplicationConfig,
    route: SpaRouteDefinition
  ): SpaRouteRequest {
    return SpaRouteRequest(
      applicationId = application.applicationId,
      routeId = route.id,
      method = serverRequest.method().name(),
      path = serverRequest.path(),
      pathParameters = serverRequest.pathVariables(),
      queryParameters = serverRequest.params().mapValues { (_, values) -> values.toList() },
      headers = serverRequest.headers().asHttpHeaders().let { httpHeaders ->
        httpHeaders.headerNames().associateWith { name ->
          httpHeaders.getOrEmpty(name).toList()
        }
      }
    )
  }
}
