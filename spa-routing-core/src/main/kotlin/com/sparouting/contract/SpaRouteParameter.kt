package com.sparouting.contract

data class SpaRouteParameter(
  val name: String,
  val optional: Boolean = false,
  val repeated: Boolean = false
) {
  init {
    require(name.isNotBlank()) {
      "SPA route parameter name must not be blank."
    }
  }

  fun optional(): SpaRouteParameter {
    return copy(optional = true)
  }

  fun repeated(): SpaRouteParameter {
    return copy(repeated = true)
  }
}
