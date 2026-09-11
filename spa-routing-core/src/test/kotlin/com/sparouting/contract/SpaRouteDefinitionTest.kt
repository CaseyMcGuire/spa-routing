package com.sparouting.contract

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
        parameters = listOf(SpaRouteParameter("id"))
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
          SpaRouteParameter("id"),
          SpaRouteParameter("id")
        )
      )
    }
  }

  @Test
  fun `accepts string parameter values without format validation`() {
    val route = SpaRouteDefinition(
      path = "users/{id}",
      id = "UserDetail",
      parameters = listOf(SpaRouteParameter("id"))
    )

    assertTrue(route.hasValidParameterValues(mapOf("id" to "42")))
    assertTrue(route.hasValidParameterValues(mapOf("id" to "user-42")))
    assertTrue(route.hasValidParameterValues(mapOf("id" to "9223372036854775807")))
    assertTrue(route.hasValidParameterValues(mapOf("id" to "0042")))
    assertTrue(route.hasValidParameterValues(mapOf("id" to "550e8400-e29b-41d4-a716-446655440000")))
    assertFalse(route.hasValidParameterValues(emptyMap()))
    assertFalse(route.hasValidParameterValues(mapOf("id" to "42", "unknown" to "value")))
  }

  @Test
  fun `optional string parameters may be omitted`() {
    val route = route("users/{id}/{tab}", "UserDetail", listOf(string("id"), string("tab").optional()))

    assertTrue(route.hasValidParameterValues(mapOf("id" to "user-42")))
    assertTrue(route.hasValidParameterValues(mapOf("id" to "user-42", "tab" to "billing")))
    assertFalse(route.hasValidParameterValues(mapOf("tab" to "billing")))
  }
}
