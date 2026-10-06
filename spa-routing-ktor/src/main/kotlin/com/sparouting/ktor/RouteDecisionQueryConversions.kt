package com.sparouting.ktor

private const val ROUTE_PARAMETER_PREFIX = "parameters."
private const val QUERY_STRING_PREFIX = "queryString."

internal fun Map<String, List<String>>.firstValueOrEmpty(name: String): String {
  return this[name]?.firstOrNull().orEmpty()
}

internal fun Map<String, List<String>>.toRouteDecisionPathParameters(): Map<String, String> {
  return filterKeys { it.startsWith(ROUTE_PARAMETER_PREFIX) }
    .mapKeys { (name, _) -> name.removePrefix(ROUTE_PARAMETER_PREFIX) }
    .mapValues { (_, values) -> values.firstOrNull().orEmpty() }
}

internal fun Map<String, List<String>>.toRouteDecisionQueryString(): Map<String, List<String>> {
  return filterKeys { it.startsWith(QUERY_STRING_PREFIX) }
    .mapKeys { (name, _) -> name.removePrefix(QUERY_STRING_PREFIX) }
}
