import com.sparouting.contract.AccessDecision
import com.sparouting.contract.DenialReason
import com.sparouting.contract.RouteAccessContext
import generated.accesstest.*

fun verifyAccess(): String {
  val denialReason = DenialReason(code = "access_denied", message = "You cannot view this route.")
  val context = RouteAccessContext(
    method = "GET",
    path = "/access/posts/42",
    pathParameters = mapOf("postId" to "42"),
    queryString = mapOf("q" to listOf("hello + 雪"), "tag" to listOf("one", "two"), "extra" to listOf("raw")),
    headers = mapOf("X-User" to listOf("casey"))
  )
  val post = object : PostAccessHandler() {
    override fun evaluate(request: PostRequest): AccessDecision {
      val postId: String = request.postId
      val q: String = request.queryString.q
      val tags: List<String> = request.queryString.tag
      val sort: String? = request.queryString.sort
      val filter: List<String>? = request.queryString.filter
      check(postId == "42" && q == "hello + 雪" && tags == listOf("one", "two"))
      check(sort == null && filter == null)
      check(request.context.header("x-user") == listOf("casey"))
      check(request.context.queryString["extra"] == listOf("raw"))
      return AccessDecision.Denied(reason = denialReason, destination = Public())
    }
  }
  check(post.route === Post)
  check(post.evaluateRequest(context) == AccessDecision.Denied(reason = denialReason, destination = Public()))
  check(post.evaluateRequest(context.copy(queryString = context.queryString + ("sort" to emptyList()))) == AccessDecision.Denied(reason = denialReason, destination = Public()))

  val start = object : StartAccessHandler() {
    override fun evaluate(request: StartRequest): AccessDecision {
      check(request.context.method == "GET")
      return AccessDecision.Allowed
    }
  }
  check(start.evaluateRequest(context.copy(pathParameters = emptyMap())) == AccessDecision.Allowed)

  val optional = object : OptionalAccessHandler() {
    override fun evaluate(request: OptionalRequest): AccessDecision {
      val id: String? = request.id
      check(id == null)
      return AccessDecision.Allowed
    }
  }
  check(optional.evaluateRequest(context.copy(pathParameters = emptyMap())) == AccessDecision.Allowed)

  val names = object : NamesAccessHandler() {
    override fun evaluate(request: NamesRequest): AccessDecision {
      check(request.context == "path context")
      check(request.queryString == "path query")
      check(request.`class` == "path keyword")
      check(request.queryString_.`class` == "query keyword")
      check(request.context_.header("X-USER") == listOf("casey"))
      return AccessDecision.Allowed
    }
  }
  check(names.evaluateRequest(context.copy(
    pathParameters = mapOf("context" to "path context", "queryString" to "path query", "class" to "path keyword"),
    queryString = mapOf("class" to listOf("query keyword"))
  )) == AccessDecision.Allowed)

  // Route IDs may also be names of the library's core types.
  val routeHandler = object : generated.accesstest.RouteAccessHandler() {
    override fun evaluate(request: generated.accesstest.RouteRequest): AccessDecision {
      return AccessDecision.Denied(reason = denialReason, destination = generated.accesstest.RouteTarget())
    }
  }
  check(routeHandler.route === generated.accesstest.Route)
  check(routeHandler.evaluateRequest(context) == AccessDecision.Denied(reason = denialReason, destination = generated.accesstest.RouteTarget()))
  val contextHandler = object : RouteAccessContextAccessHandler() {
    override fun evaluate(request: RouteAccessContextRequest): AccessDecision {
      check(request.queryString.q == "hello + 雪")
      return AccessDecision.Allowed
    }
  }
  check(contextHandler.route === generated.accesstest.RouteAccessContext)
  check(contextHandler.evaluateRequest(context) == AccessDecision.Allowed)
  return "access verified"
}
