package com.sparouting.examples.blog

/** Framework-neutral blog operations, validation, and in-memory storage. */
class BlogPostService {
  private val posts = linkedMapOf(
    "1" to BlogPost(
      id = "1",
      title = "Welcome to the blog",
      body = "This example shares its routes between the Kotlin server and the browser. Try creating or editing a post."
    ),
    "2" to BlogPost(
      id = "2",
      title = "Routes and redirects",
      body = "Each post has a reader and an editor route. Missing posts redirect to the not-found page."
    )
  )
  private var nextId = 3L

  @Synchronized
  fun list(query: String?): List<BlogPost> {
    val search = query?.trim().orEmpty()
    return posts.values.filter { post ->
      post.title.contains(search, ignoreCase = true) || post.body.contains(search, ignoreCase = true)
    }.reversed()
  }

  @Synchronized
  fun find(postId: String): BlogPost? {
    return posts[postId]
  }

  @Synchronized
  fun create(input: WritePostRequest): BlogPost {
    validate(input)
    val post = BlogPost(
      id = (nextId++).toString(),
      title = input.title,
      body = input.body
    )
    posts[post.id] = post
    return post
  }

  @Synchronized
  fun update(postId: String, input: WritePostRequest): BlogPost? {
    validate(input)
    if (!posts.containsKey(postId)) {
      return null
    }

    val post = BlogPost(id = postId, title = input.title, body = input.body)
    posts[postId] = post
    return post
  }

  @Synchronized
  fun delete(postId: String): Boolean {
    return posts.remove(postId) != null
  }

  private fun validate(input: WritePostRequest) {
    if (input.title.isBlank() || input.body.isBlank()) {
      throw InvalidPostException("Title and body must not be blank")
    }
  }
}
