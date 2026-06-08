package com.caseymcguiredotcom.sparoutecontract

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpaRouteDefinitionTest {
  @Test
  fun `rejects missing parameter metadata`() {
    assertFailsWith<IllegalArgumentException> {
      SpaRouteDefinition(
        path = "users/{id}",
        id = "UserDetail"
      )
    }
  }

  @Test
  fun `rejects extra parameter metadata`() {
    assertFailsWith<IllegalArgumentException> {
      SpaRouteDefinition(
        path = "users",
        id = "UserList",
        parameters = listOf(SpaRouteParameter("id", SpaRouteParameterType.INT))
      )
    }
  }

  @Test
  fun `rejects duplicate parameter metadata`() {
    assertFailsWith<IllegalArgumentException> {
      SpaRouteDefinition(
        path = "users/{id}",
        id = "UserDetail",
        parameters = listOf(
          SpaRouteParameter("id", SpaRouteParameterType.INT),
          SpaRouteParameter("id", SpaRouteParameterType.INT)
        )
      )
    }
  }

  @Test
  fun `validates int parameter values`() {
    val route = SpaRouteDefinition(
      path = "users/{id}",
      id = "UserDetail",
      parameters = listOf(SpaRouteParameter("id", SpaRouteParameterType.INT))
    )

    assertTrue(route.hasValidParameterValues(mapOf("id" to "42")))
    assertFalse(route.hasValidParameterValues(mapOf("id" to "not-an-int")))
  }
}
