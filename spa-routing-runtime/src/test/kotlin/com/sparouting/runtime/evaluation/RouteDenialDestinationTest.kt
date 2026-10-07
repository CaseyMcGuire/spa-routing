package com.sparouting.runtime.evaluation

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.DenialReason
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.RouteManifest
import com.sparouting.runtime.testsupport.applicationAccessHandler
import com.sparouting.contract.RouteRequest
import com.sparouting.runtime.testsupport.TestSinglePageApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private val denialReason = DenialReason(code = "access_denied", message = "You cannot view this route.")

class RouteDenialDestinationTest {
  @Test
  fun `typed denial destination resolves to full url`() {
    for (id in listOf("user-42", "0042", "550e8400-e29b-41d4-a716-446655440000")) {
      val response = evaluateDenial(RouteTarget("test", "UserDetail", mapOf("id" to id)))

      assertEquals(RouteResult.Denied(reason = denialReason, destinationUrl = "/test/users/$id"), response)
    }
  }

  @Test
  fun `unknown target app throws`() {
    assertFailsWith<IllegalStateException> {
      evaluateDenial(RouteTarget("missing", "UserDetail", mapOf("id" to "42")))
    }
  }

  @Test
  fun `unknown target route throws`() {
    assertFailsWith<IllegalStateException> {
      evaluateDenial(RouteTarget("test", "Missing", mapOf("id" to "42")))
    }
  }

  @Test
  fun `invalid target params throws`() {
    for (parameters in listOf(emptyMap(), mapOf("id" to "user-42", "unknown" to "value"))) {
      assertFailsWith<IllegalArgumentException> {
        evaluateDenial(RouteTarget("test", "UserDetail", parameters))
      }
    }
  }

  private fun evaluateDenial(target: RouteTarget): RouteResult {
    val config = TestSinglePageApplicationConfig(
      routes = listOf(RouteManifest("/test/users/{id}", "UserDetail"))
    )
    val handler = applicationAccessHandler { AccessDecision.Denied(reason = denialReason, destination = target) }
    return RouteRequestEvaluator(listOf(config.copy(applicationAccessHandler = handler))).evaluate(
      RouteRequest("test", "UserDetail", mapOf("id" to "42"))
    )
  }
}
