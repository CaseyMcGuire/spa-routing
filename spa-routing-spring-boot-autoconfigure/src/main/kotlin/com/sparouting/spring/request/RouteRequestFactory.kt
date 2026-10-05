package com.sparouting.spring.request

import com.sparouting.contract.RouteManifest
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.contract.RouteRequest
import org.springframework.web.servlet.function.ServerRequest

interface RouteRequestFactory {
  fun create(
    serverRequest: ServerRequest,
    application: SinglePageApplicationConfig,
    route: RouteManifest
  ): RouteRequest
}
