package com.sparouting.examples.spring.blog

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.net.URI

@RestController
@RequestMapping("/api/posts")
class BlogPostController(private val posts: BlogPostStore) {
  @GetMapping
  fun list(@RequestParam(name = "q", required = false) query: String?): List<BlogPost> {
    return posts.list(query)
  }

  @GetMapping("/{postId}")
  fun get(@PathVariable("postId") postId: String): BlogPost {
    return posts.find(postId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found")
  }

  @PostMapping
  fun create(@RequestBody input: WritePostRequest): ResponseEntity<BlogPost> {
    validate(input)
    val post = posts.create(input)
    return ResponseEntity.created(URI.create("/api/posts/${post.id}")).body(post)
  }

  @PutMapping("/{postId}")
  fun update(
    @PathVariable("postId") postId: String,
    @RequestBody input: WritePostRequest
  ): BlogPost {
    validate(input)
    return posts.update(postId, input) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found")
  }

  @DeleteMapping("/{postId}")
  fun delete(@PathVariable("postId") postId: String): ResponseEntity<Void> {
    if (!posts.delete(postId)) {
      throw ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found")
    }
    return ResponseEntity.noContent().build()
  }

  private fun validate(input: WritePostRequest) {
    if (input.title.isBlank() || input.body.isBlank()) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Title and body must not be blank")
    }
  }
}
