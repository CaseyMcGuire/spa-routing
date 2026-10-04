package io.github.caseymcguire.sparouting.spring.request

import com.sparouting.contract.RouteDefinition
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import org.springframework.web.servlet.function.ServerRequest

interface RouteRequestFactory {
  fun create(
    serverRequest: ServerRequest,
    application: SinglePageApplicationConfig,
    route: RouteDefinition
  ): RouteRequest
}
