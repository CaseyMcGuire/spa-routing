import com.sparouting.contract.codegen.QueryGeneratorTestApplication
import generated.QueryTestRoutes
import generated.querytest.*

fun verifyQueries(): String {
  check(QueryTestRoutes.Home() == Home())
  check(QueryString(QueryString.QueryString(q = "term")).queryString == mapOf("q" to listOf("term")))
  check(QueryStringKey(QueryStringKey.QueryString(q = "term")).queryString == mapOf("q" to listOf("term")))
  check(OptionalQuery().queryString.isEmpty())
  check(OptionalQuery(OptionalQuery.QueryString(tag = emptyList())).queryString.isEmpty())
  check(OptionalMixed(id = "42").queryString.isEmpty())
  check(Search(Search.QueryString(q = "term")).queryString == mapOf("q" to listOf("term")))
  check(runCatching { Filters(Filters.QueryString(tag = emptyList())) }.exceptionOrNull() is IllegalArgumentException)

  val tags = mutableListOf("one", "two")
  val filters = Filters(Filters.QueryString(tag = tags))
  tags.clear()
  check(filters.queryString["tag"] == listOf("one", "two"))

  val sameName = SameName(queryString = "path", queryString_ = "path2", queryString__ = SameName.QueryString(queryString = "wire"))
  check(sameName.parameters == mapOf("queryString" to "path", "queryString_" to "path2"))
  check(sameName.queryString == mapOf("queryString" to listOf("wire")))

  val special = Special(Special.QueryString(a_b = listOf("one", "two"), `class` = "keyword", __proto__ = "literal", a___ = "escaped"))
  check(special.queryString["a b"] == listOf("one", "two"))
  check(special.queryString["class"] == listOf("keyword"))
  check(special.queryString["__proto__"] == listOf("literal"))
  check(special.queryString["a\"$\n"] == listOf("escaped"))
  check(Special.QueryStringKey.A_B.wireName == "a b")

  val raw = mapOf("foo" to listOf(""), "tag" to listOf("a", "b"), "utm_source" to listOf("extra"))
  val queryString = QueryTestRoutes.UserDetail.queryString(raw)
  check(queryString[UserDetail.QueryStringKey.FOO] == listOf(""))
  check(queryString[UserDetail.QueryStringKey.TAG] == listOf("a", "b"))
  check(UserDetail.QueryStringKey.BAZ !in queryString)
  check(queryString.size == 2)
  check(raw["utm_source"] == listOf("extra"))

  val target = QueryTestRoutes.UserDetail("123", UserDetail.QueryString(foo = "a b+&=雪", baz = "", tag = listOf("x/y", "é")))
  val definition = QueryGeneratorTestApplication.routes.first { it.id == "UserDetail" }
  return definition.resolvePath(QueryGeneratorTestApplication.getFullPathPattern(definition), target.parameters) +
    "?" + definition.resolveQueryString(target.queryString)
}
