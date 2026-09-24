import com.sparouting.contract.codegen.QueryGeneratorTestApplication
import generated.QueryTestRoutes
import generated.querytest.*

fun verifyQueries(): String {
  check(QueryTestRoutes.Home() == Home())
  check(Query(Query.Query(q = "term")).queryParameters == mapOf("q" to listOf("term")))
  check(QueryKey(QueryKey.Query(q = "term")).queryParameters == mapOf("q" to listOf("term")))
  check(OptionalQuery().queryParameters.isEmpty())
  check(OptionalQuery(OptionalQuery.Query(tag = emptyList())).queryParameters.isEmpty())
  check(OptionalMixed(id = "42").queryParameters.isEmpty())
  check(Search(Search.Query(q = "term")).queryParameters == mapOf("q" to listOf("term")))
  check(runCatching { Filters(Filters.Query(tag = emptyList())) }.exceptionOrNull() is IllegalArgumentException)

  val tags = mutableListOf("one", "two")
  val filters = Filters(Filters.Query(tag = tags))
  tags.clear()
  check(filters.queryParameters["tag"] == listOf("one", "two"))

  val sameName = SameName(query = "path", query_ = "path2", query__ = SameName.Query(query = "wire"))
  check(sameName.parameters == mapOf("query" to "path", "query_" to "path2"))
  check(sameName.queryParameters == mapOf("query" to listOf("wire")))

  val special = Special(Special.Query(a_b = listOf("one", "two"), `class` = "keyword", __proto__ = "literal", a___ = "escaped"))
  check(special.queryParameters["a b"] == listOf("one", "two"))
  check(special.queryParameters["class"] == listOf("keyword"))
  check(special.queryParameters["__proto__"] == listOf("literal"))
  check(special.queryParameters["a\"$\n"] == listOf("escaped"))
  check(Special.QueryKey.A_B.wireName == "a b")

  val raw = mapOf("foo" to listOf(""), "tag" to listOf("a", "b"), "utm_source" to listOf("extra"))
  val query = QueryTestRoutes.UserDetail.queryParameters(raw)
  check(query[UserDetail.QueryKey.FOO] == listOf(""))
  check(query[UserDetail.QueryKey.TAG] == listOf("a", "b"))
  check(UserDetail.QueryKey.BAZ !in query)
  check(query.size == 2)
  check(raw["utm_source"] == listOf("extra"))

  val target = QueryTestRoutes.UserDetail("123", UserDetail.Query(foo = "a b+&=雪", baz = "", tag = listOf("x/y", "é")))
  val definition = QueryGeneratorTestApplication.routes.first { it.id == "UserDetail" }
  return definition.resolvePath(QueryGeneratorTestApplication.getFullPathPattern(definition), target.parameters) +
    "?" + definition.resolveQueryString(target.queryParameters)
}
