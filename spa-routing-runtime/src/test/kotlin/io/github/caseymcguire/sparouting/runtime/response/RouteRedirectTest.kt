package io.github.caseymcguire.sparouting.runtime.response

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.RouteManifest
import io.github.caseymcguire.sparouting.runtime.testsupport.applicationAccessHandler
import io.github.caseymcguire.sparouting.runtime.access.RouteAccessEvaluator
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.testsupport.TestSinglePageApplicationManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RouteRedirectTest {
  @Test
  fun `typed route redirect resolves to full url`() {
    for (id in listOf("user-42", "0042", "550e8400-e29b-41d4-a716-446655440000")) {
      val response = redirect(RouteTarget("test", "UserDetail", mapOf("id" to id)))

      assertEquals(302, response.statusCode)
      assertEquals("/test/users/$id", response.location)
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

  private fun redirect(target: RouteTarget): RouteHttpResponse {
    val config = TestSinglePageApplicationConfig(
      manifest = TestSinglePageApplicationManifest(
        routes = listOf(RouteManifest("/test/users/{id}", "UserDetail"))
      )
    )
    val handler = applicationAccessHandler(config.manifest) { AccessDecision.Redirect(target) }
    val registry = SinglePageApplicationRouteRegistry(listOf(config), listOf(handler))
    return RouteResponseService(registry, RouteAccessEvaluator(registry)).evaluate(
      RouteResponseRequest("test", "UserDetail", mapOf("id" to "42"))
    )
  }
}
