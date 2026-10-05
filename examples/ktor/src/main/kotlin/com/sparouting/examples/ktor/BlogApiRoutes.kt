package com.sparouting.examples.ktor

import com.sparouting.examples.blog.BlogPostService
import com.sparouting.examples.blog.WritePostRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.blogApiRoutes(posts: BlogPostService) {
  route("/api/posts") {
    get {
      call.respond(posts.list(call.request.queryParameters["q"]))
    }
    get("/{postId}") {
      val post = posts.find(call.pathParameters["postId"].orEmpty())
        ?: throw NotFoundException("Post not found")
      call.respond(post)
    }
    post {
      val post = posts.create(call.receive<WritePostRequest>())
      call.response.headers.append(HttpHeaders.Location, "/api/posts/${post.id}")
      call.respond(HttpStatusCode.Created, post)
    }
    put("/{postId}") {
      val post = posts.update(
        postId = call.pathParameters["postId"].orEmpty(),
        input = call.receive<WritePostRequest>()
      ) ?: throw NotFoundException("Post not found")
      call.respond(post)
    }
    delete("/{postId}") {
      if (!posts.delete(call.pathParameters["postId"].orEmpty())) {
        throw NotFoundException("Post not found")
      }
      call.respond(HttpStatusCode.NoContent)
    }
  }
}
