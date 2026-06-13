package io.github.caseymcguire.sparouting.spring.request

import org.springframework.web.servlet.function.ServerRequest

internal fun ServerRequest.toSpaRouteHeaders(): Map<String, List<String>> {
  val httpHeaders = headers().asHttpHeaders()
  return httpHeaders.headerNames().associateWith { name ->
    httpHeaders.getOrEmpty(name).toList()
  }
}
