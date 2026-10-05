package com.sparouting.examples.ktor

import com.sparouting.examples.blog.BlogPostService
import com.sparouting.examples.blog.CheckBlogAccess
import com.sparouting.examples.blog.CheckEditPostAccess
import com.sparouting.examples.blog.CheckPostAccess
import com.sparouting.examples.blog.InvalidPostException
import com.sparouting.examples.generated.routes.BlogApplicationConfig
import com.sparouting.examples.generated.routes.BlogRouteAccessHandlers
import com.sparouting.ktor.singlePageApplicationRoutes
import com.sparouting.runtime.rendering.HtmlDocumentRenderer
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing

fun main() {
  val port = System.getenv("PORT")?.toInt() ?: 8082
  embeddedServer(Netty, host = "127.0.0.1", port = port, module = Application::blogModule)
    .start(wait = true)
}

fun Application.blogModule() {
  val posts = BlogPostService()
  val config = BlogApplicationConfig(
    applicationAccessHandler = CheckBlogAccess(),
    routeAccessHandlers = BlogRouteAccessHandlers(
      post = CheckPostAccess(posts),
      editPost = CheckEditPostAccess(posts)
    ),
    htmlRenderer = HtmlDocumentRenderer(globalStylesheet = null)
  )

  install(ContentNegotiation) {
    jackson()
  }
  install(StatusPages) {
    exception<InvalidPostException> { call, exception ->
      call.respond(HttpStatusCode.BadRequest, mapOf("message" to exception.message.orEmpty()))
    }
  }

  routing {
    blogApiRoutes(posts)
    singlePageApplicationRoutes(configs = listOf(config))
    staticResources("/bundles", "static/bundles")
  }
}
