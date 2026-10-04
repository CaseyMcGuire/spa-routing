package com.sparouting.contract

/** Defines a route with string path parameters inferred from its placeholders. */
fun route(
  path: String,
  id: String,
  parameters: List<SpaRouteParameter> = SpaRouteDefinition.inferPathParameters(path),
  queryString: List<SpaRouteParameter> = emptyList(),
  generateAccessHandler: Boolean = false
): SpaRouteDefinition {
  return SpaRouteDefinition(
    path = path,
    id = id,
    parameters = parameters,
    queryString = queryString,
    generateAccessHandler = generateAccessHandler
  )
}

/** Declares a required, single string value; use optional() or repeated() to change its cardinality. */
fun parameter(name: String): SpaRouteParameter {
  return SpaRouteParameter(name)
}
