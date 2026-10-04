import com.sparouting.contract.RouteDecision
import com.sparouting.contract.SpaRouteAccessContext
import generated.accesstest.*

fun verifyAccess(): String {
  val context = SpaRouteAccessContext(
    method = "GET",
    path = "/access/posts/42",
    pathParameters = mapOf("postId" to "42"),
    queryString = mapOf("q" to listOf("hello + 雪"), "tag" to listOf("one", "two"), "extra" to listOf("raw")),
    headers = mapOf("X-User" to listOf("casey"))
  )
  val post = object : PostAccessHandler() {
    override fun evaluate(request: PostRequest): RouteDecision {
      val postId: String = request.postId
      val q: String = request.queryString.q
      val tags: List<String> = request.queryString.tag
      val sort: String? = request.queryString.sort
      val filter: List<String>? = request.queryString.filter
      check(postId == "42" && q == "hello + 雪" && tags == listOf("one", "two"))
      check(sort == null && filter == null)
      check(request.context.header("x-user") == listOf("casey"))
      check(request.context.queryString["extra"] == listOf("raw"))
      return RouteDecision.Redirect(Public())
    }
  }
  check(post.route === Post)
  check(post.evaluateRequest(context) == RouteDecision.Redirect(Public()))
  check(post.evaluateRequest(context.copy(queryString = context.queryString + ("sort" to emptyList()))) == RouteDecision.Redirect(Public()))

  val start = object : StartAccessHandler() {
    override fun evaluate(request: StartRequest): RouteDecision {
      check(request.context.method == "GET")
      return RouteDecision.Allow
    }
  }
  check(start.evaluateRequest(context.copy(pathParameters = emptyMap())) == RouteDecision.Allow)

  val optional = object : OptionalAccessHandler() {
    override fun evaluate(request: OptionalRequest): RouteDecision {
      val id: String? = request.id
      check(id == null)
      return RouteDecision.Allow
    }
  }
  check(optional.evaluateRequest(context.copy(pathParameters = emptyMap())) == RouteDecision.Allow)

  val names = object : NamesAccessHandler() {
    override fun evaluate(request: NamesRequest): RouteDecision {
      check(request.context == "path context")
      check(request.queryString == "path query")
      check(request.`class` == "path keyword")
      check(request.queryString_.`class` == "query keyword")
      check(request.context_.header("X-USER") == listOf("casey"))
      return RouteDecision.Allow
    }
  }
  check(names.evaluateRequest(context.copy(
    pathParameters = mapOf("context" to "path context", "queryString" to "path query", "class" to "path keyword"),
    queryString = mapOf("class" to listOf("query keyword"))
  )) == RouteDecision.Allow)
  return "access verified"
}
