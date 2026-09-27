package com.sparouting.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpaRouteDefinitionTest {
  @Test
  fun `infers string parameters in path order`() {
    val definition = SpaRouteDefinition("users/{id}/orders/{orderId:[0-9]+}", "UserOrder")
    assertEquals(listOf(parameter("id"), parameter("orderId")), definition.parameters)
    assertEquals(definition.parameters, route(definition.path, definition.id).parameters)
    assertTrue(definition.hasValidParameterValues(mapOf("id" to "user-42", "orderId" to "0042")))
    assertFalse(definition.hasValidParameterValues(mapOf("id" to "user-42")))
  }

  @Test
  fun `static routes infer no parameters`() {
    assertTrue(route("home", "Home").parameters.isEmpty())
    assertTrue(SpaRouteDefinition("", "Index").parameters.isEmpty())
  }

  @Test
  fun `rejects duplicate and colliding inferred path names`() {
    assertFailsWith<IllegalArgumentException> {
      route("users/{id}/orders/{id}", "UserOrder")
    }
    assertFailsWith<IllegalArgumentException> {
      route("users/{user-id}/{user_id}", "UserDetail")
    }
  }

  @Test
  fun `rejects incomplete explicit parameter metadata`() {
    assertFailsWith<IllegalArgumentException> {
      SpaRouteDefinition(
        path = "users/{id}",
        id = "UserDetail",
        parameters = emptyList()
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
      id = "UserDetail"
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
    val route = route("users/{id}/{tab}", "UserDetail", listOf(parameter("id"), parameter("tab").optional()))

    assertTrue(route.hasValidParameterValues(mapOf("id" to "user-42")))
    assertTrue(route.hasValidParameterValues(mapOf("id" to "user-42", "tab" to "billing")))
    assertFalse(route.hasValidParameterValues(mapOf("tab" to "billing")))
  }
}
