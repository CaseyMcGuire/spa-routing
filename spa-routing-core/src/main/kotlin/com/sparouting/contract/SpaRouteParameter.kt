package com.sparouting.contract

data class SpaRouteParameter(
  val name: String,
  val optional: Boolean = false
) {
  init {
    require(name.isNotBlank()) {
      "SPA route parameter name must not be blank."
    }
  }

  fun optional(): SpaRouteParameter {
    return copy(optional = true)
  }
}
