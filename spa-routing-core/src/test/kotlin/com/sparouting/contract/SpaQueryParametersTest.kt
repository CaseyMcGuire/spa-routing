package com.sparouting.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpaQueryParametersTest {
  private val route = route("search", "Search", queryParameters = listOf(
    string("q"), string("tab").optional(), string("tag").repeated(), string("filter").optional().repeated()
  ))

  @Test
  fun `validates presence and cardinality without restricting values or extra keys`() {
    val valid = mapOf("q" to listOf(""), "tag" to listOf("a", "b"))
    assertTrue(route.hasValidQueryParameterValues(valid))
    assertTrue(route.hasValidQueryParameterValues(valid + mapOf("utm_source" to listOf("a", "b"))))
    assertTrue(route.hasValidQueryParameterValues(valid + mapOf("filter" to emptyList())))
    assertTrue(route.hasValidQueryParameterValues(valid + mapOf("filter" to listOf("", "x"))))
    assertFalse(route.hasValidQueryParameterValues(valid - "q"))
    assertFalse(route.hasValidQueryParameterValues(valid + mapOf("q" to emptyList())))
    assertFalse(route.hasValidQueryParameterValues(valid + mapOf("q" to listOf("a", "b"))))
    assertFalse(route.hasValidQueryParameterValues(valid + mapOf("tab" to listOf("a", "b"))))
    assertFalse(route.hasValidQueryParameterValues(valid - "tag"))
    assertFalse(route.hasValidQueryParameterValues(valid + mapOf("tag" to emptyList())))
  }

  @Test
  fun `optional and repeated compose in either order`() {
    assertEquals(string("tag").optional().repeated(), string("tag").repeated().optional())
  }

  @Test
  fun `routes without declarations accept arbitrary query parameters`() {
    assertTrue(route("home", "Home").hasValidQueryParameterValues(mapOf("anything" to listOf("a", "b"))))
  }

  @Test
  fun `rejects repeated path parameters and duplicate query declarations`() {
    assertFailsWith<IllegalArgumentException> {
      route("users/{id}", "UserDetail", parameters = listOf(string("id").repeated()))
    }
    assertFailsWith<IllegalArgumentException> {
      route("search", "Search", queryParameters = listOf(string("q"), string("q").optional()))
    }
  }

  @Test
  fun `rejects generated property and enum collisions`() {
    for (names in listOf(listOf("a-b", "a_b"), listOf("foo", "FOO"))) {
      assertFailsWith<IllegalArgumentException> {
        route("search", "Search", queryParameters = names.map { string(it) })
      }
    }
  }

  @Test
  fun `path and query declarations have independent names`() {
    val route = route("users/{id}", "UserDetail", listOf(string("id")), listOf(string("id")))
    assertTrue(route.hasValidParameterValues(mapOf("id" to "path")))
    assertTrue(route.hasValidQueryParameterValues(mapOf("id" to listOf("query"))))
  }

  @Test
  fun `encodes names values and repeated keys as form query strings`() {
    val route = route("search", "Search", queryParameters = listOf(string("a b").repeated(), string("empty").optional()))
    assertEquals(
      "a+b=x%2By%26%3D%3F%23%2F&a+b=%C3%A9%E9%9B%AA%F0%9F%98%80%7E*&empty=",
      route.resolveQueryString(linkedMapOf("a b" to listOf("x+y&=?#/", "é雪😀~*"), "empty" to listOf("")))
    )
    assertFailsWith<IllegalArgumentException> { route.resolveQueryString(emptyMap()) }
    assertEquals("", route("home", "Home").resolveQueryString(mapOf("optional" to emptyList())))
  }
}
