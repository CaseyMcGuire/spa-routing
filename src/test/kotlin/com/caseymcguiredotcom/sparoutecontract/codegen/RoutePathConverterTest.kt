package com.caseymcguiredotcom.sparoutecontract.codegen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RoutePathConverterTest {
  private val converter = RoutePathConverter()

  @Test
  fun `converts route parameters to react router parameters`() {
    assertEquals(
      "/users/:id/orders/:orderId",
      converter.convertToReactRouter("/users/{id}/orders/{orderId:[0-9]+}")
    )
  }

  @Test
  fun `converts spring wildcards to react router syntax`() {
    assertEquals("/assets/*", converter.convertToReactRouter("/assets/**"))
    assertEquals("/files/:wildcard1/details", converter.convertToReactRouter("/files/*/details"))
  }

  @Test
  fun `rejects partial route parameters`() {
    assertFailsWith<IllegalArgumentException> {
      converter.convertToReactRouter("/users/user-{id}")
    }
  }
}
