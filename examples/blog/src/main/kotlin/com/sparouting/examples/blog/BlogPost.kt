package com.sparouting.examples.blog

/** JSON representation returned by the blog's list, read, create, and update endpoints. */
data class BlogPost(
  val id: String,
  val title: String,
  val body: String
)

/** JSON input shared by post creation and replacement; both fields must be nonblank. */
data class WritePostRequest(
  val title: String,
  val body: String
)
