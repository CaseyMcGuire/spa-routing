package com.sparouting.contract

fun route(
  path: String,
  id: String,
  parameters: List<SpaRouteParameter> = emptyList(),
  queryParameters: List<SpaRouteParameter> = emptyList()
): SpaRouteDefinition {
  return SpaRouteDefinition(path, id, parameters, queryParameters)
}

fun string(name: String): SpaRouteParameter {
  return SpaRouteParameter(name)
}
