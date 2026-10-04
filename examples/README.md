# Examples

The blog example exercises the library projects in this checkout. It shares
route definitions in `route-definitions`, so another server example can reuse
the same routes. The current server is Spring Boot.

The route contracts, generation, and Spring application shell are implemented. The
in-memory store, REST endpoints, and spa-kit UI are the next stages; the current
page is a small JavaScript shell displaying its URL.

```text
examples/
├── route-definitions/  Shared blog route definitions
└── spring/             Spring Boot application and post models
```

## Run the Spring example

Use JDK 21 and run this command from the repository root:

```sh
./gradlew :examples:spring:run
```

Open [http://localhost:8080/](http://localhost:8080/) or
[http://localhost:8080/posts/123](http://localhost:8080/posts/123).
Both routes serve the default SPA HTML shell. Stop the server with Ctrl+C.

To use another port:

```sh
./gradlew :examples:spring:run --args='--server.port=8081'
```

The Spring application uses the local `spa-routing-spring-boot-starter` project
and exposes a `SinglePageApplicationConfig` bean. Its application-level
`AllowAll()` rule explicitly allows access to the example routes. Removing that
rule makes the application gate deny requests with `404`.

## Route contracts and generated builders

| Route | URL | Values |
| --- | --- | --- |
| `Index` | `/` | Optional scalar search query `q` |
| `Post` | `/posts/{postId}` | Required string `postId` |
| `NewPost` | `/new` | None |
| `EditPost` | `/posts/{postId}/edit` | Required string `postId` |
| `NotFound` | `/not-found` | None |
| `Error` | `/error` | None |

The required path parameters are inferred from their placeholders. Repeating
`q` is invalid and returns `400`. A well-formed route currently serves the shell
without checking whether a post exists; existence checks will use route rules
once the store is implemented.

The example invokes this checkout's generator entry points through Gradle
`JavaExec` tasks. Generated files stay under `spring/build/generated` and are
not checked in. Kotlin compilation automatically generates the server routes.
Use the tasks below to generate both server and client routes explicitly.

```sh
./gradlew :examples:spring:generateServerSpaRoutes :examples:spring:generateClientRoutes
```

Server output is in `spring/build/generated/source/spaRoutes/main`. Client
output is in `spring/build/generated/client/routes/BlogRoutes.ts`.

```kotlin
import com.sparouting.examples.generated.routes.BlogRoutes
import com.sparouting.examples.generated.routes.blog.Index

val post = BlogRoutes.Post(postId = "123")
val search = BlogRoutes.Index(queryString = Index.QueryString(q = "kotlin"))
```

```typescript
BlogRoutes.Post({ postId: "123" }); // /posts/123
BlogRoutes.Index({ q: "hello world" }); // /?q=hello+world
BlogRoutes.Index.parse({}, new URLSearchParams("q=kotlin"));
// { params: {}, queryString: { q: "kotlin" } }
```

## Planned REST contract

`BlogPost` contains string fields `id`, `title`, and `body`. `WritePostRequest`
contains `title` and `body`, both required and nonblank. These Kotlin models
are defined in the Spring example; the endpoints below are not implemented yet.

| Method | URL | Successful response |
| --- | --- | --- |
| `GET` | `/api/posts?q=...` | `200`, array of posts; optional title/body search |
| `GET` | `/api/posts/{postId}` | `200`, post |
| `POST` | `/api/posts` | `201`, created post and API `Location` header |
| `PUT` | `/api/posts/{postId}` | `200`, replaced post |
| `DELETE` | `/api/posts/{postId}` | `204`, no body |

Missing posts return `404`; malformed or blank write inputs return `400`.
The server assigns IDs. The store will start with sample posts and reset on
restart. The UI will use plain text for post bodies and basic list/read/edit
forms, keeping attention on route generation, rules, and client navigation.

## Check a route decision

With the server running:

```sh
curl --get 'http://localhost:8080/__spa/route-decision' \
  --data-urlencode 'applicationId=blog' \
  --data-urlencode 'routeId=Post' \
  --data-urlencode 'parameters.postId=123'
```

The response body contains `"statusCode": 200`. Omitting `parameters.postId` produces
`"statusCode": 400`. The route-decision endpoint itself returns HTTP `200` in both
cases; its response body describes whether navigation is allowed.

For the search route, use `routeId=Index` and `queryString.q=kotlin`.

## Build

```sh
./gradlew :examples:build
```

The repository-wide `./gradlew build` also builds these projects.
The example projects have no publishing configuration.
