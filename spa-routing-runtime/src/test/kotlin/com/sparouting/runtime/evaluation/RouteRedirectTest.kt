package com.sparouting.runtime.evaluation

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.RouteManifest
import com.sparouting.runtime.testsupport.applicationAccessHandler
import com.sparouting.contract.RouteRequest
import com.sparouting.runtime.testsupport.TestSinglePageApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RouteRedirectTest {
  @Test
  fun `typed route redirect resolves to full url`() {
    for (id in listOf("user-42", "0042", "550e8400-e29b-41d4-a716-446655440000")) {
      val response = redirect(RouteTarget("test", "UserDetail", mapOf("id" to id)))

      assertEquals(RouteResult.Redirect("/test/users/$id"), response)
    }
  }

  @Test
  fun `unknown target app throws`() {
    assertFailsWith<IllegalStateException> {
      redirect(RouteTarget("missing", "UserDetail", mapOf("id" to "42")))
    }
  }

  @Test
  fun `unknown target route throws`() {
    assertFailsWith<IllegalStateException> {
      redirect(RouteTarget("test", "Missing", mapOf("id" to "42")))
    }
  }

  @Test
  fun `invalid target params throws`() {
    for (parameters in listOf(emptyMap(), mapOf("id" to "user-42", "unknown" to "value"))) {
      assertFailsWith<IllegalArgumentException> {
        redirect(RouteTarget("test", "UserDetail", parameters))
      }
    }
  }

  private fun redirect(target: RouteTarget): RouteResult {
    val config = TestSinglePageApplicationConfig(
      routes = listOf(RouteManifest("/test/users/{id}", "UserDetail"))
    )
    val handler = applicationAccessHandler { AccessDecision.Redirect(target) }
    return RouteRequestEvaluator(listOf(config.copy(applicationAccessHandler = handler))).evaluate(
      RouteRequest("test", "UserDetail", mapOf("id" to "42"))
    )
  }
}
