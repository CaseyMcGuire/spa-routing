# Examples

The blog example exercises the library projects in this checkout. It shares
route definitions in `route-definitions`, so another server example can reuse
the same routes. The current server is Spring Boot.

The route contracts, generation, in-memory store, REST controller, route rules,
and React components are implemented. Frontend integration is next; the running
Spring page still uses the small JavaScript shell displaying its URL.

```text
examples/
├── route-definitions/  Shared blog route definitions
└── spring/             Spring Boot application, in-memory store, and REST API
    └── frontend/       React components for the blog UI
```

## Run the Spring example

Use JDK 21 and run this command from the repository root:

```sh
./gradlew :examples:spring:run
```

Open [http://localhost:8080/](http://localhost:8080/) or
[http://localhost:8080/posts/1](http://localhost:8080/posts/1).
Both routes serve the default SPA HTML shell. Stop the server with Ctrl+C.

To use another port:

```sh
./gradlew :examples:spring:run --args='--server.port=8081'
```

The Spring application uses the local `spa-routing-spring-boot-starter` project
and exposes a `SinglePageApplicationConfig` bean. Its application-level
`AllowAll()` rule explicitly allows access to the example routes. Removing that
rule makes the application gate deny requests with `404`.

Spring injects the `PostExists` rule into the configuration. It is registered
against the generated `BlogRoutes.Post` and `BlogRoutes.EditPost` keys and
checks the same store used by the API. Missing posts redirect to the generated
`BlogRoutes.NotFound()` target; existing posts fall through with `Skip`.

Boot's servlet error endpoint is configured at `/internal/error` so it does
not collide with the SPA's `/error` page. Otherwise an API error dispatch can
serve the SPA shell with status `200`.

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
`q` is invalid and returns `400`. Post reader and editor routes return a `302`
redirect to `/not-found` when the post does not exist. The navigation decision
endpoint reports the same redirect in its JSON body.

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

## REST API

`BlogPost` contains string fields `id`, `title`, and `body`. `WritePostRequest`
contains `title` and `body`, both required and nonblank. These Kotlin models
are defined in the Spring example.

The Spring example includes Jackson's Kotlin module for JSON request bodies,
with its version managed by Spring Boot. This dependency belongs only to
`examples:spring`; the library modules do not depend on it.

| Method | URL | Successful response |
| --- | --- | --- |
| `GET` | `/api/posts?q=...` | `200`, array of posts; optional title/body search |
| `GET` | `/api/posts/{postId}` | `200`, post |
| `POST` | `/api/posts` | `201`, created post and API `Location` header |
| `PUT` | `/api/posts/{postId}` | `200`, replaced post |
| `DELETE` | `/api/posts/{postId}` | `204`, no body |

Missing posts return `404`; malformed or blank write inputs return `400`.
The server assigns IDs. The synchronized in-memory store starts with sample
posts `1` and `2` and resets on restart. Lists show newest posts first and search
matches title or body, ignoring case. The UI will use plain text for post bodies
and basic list/read/edit forms, keeping attention on route generation, rules,
and client navigation.

With the server running, create a post using:

```sh
curl --include 'http://localhost:8080/api/posts' \
  --header 'Content-Type: application/json' \
  --data '{"title":"A new post","body":"Hello from the blog API."}'
```

## Check a route decision

With the server running:

```sh
curl --get 'http://localhost:8080/__spa/route-decision' \
  --data-urlencode 'applicationId=blog' \
  --data-urlencode 'routeId=Post' \
  --data-urlencode 'parameters.postId=1'
```

The response body contains `"statusCode": 200`. Omitting `parameters.postId` produces
`"statusCode": 400`. The route-decision endpoint itself returns HTTP `200` in both
cases; its response body describes whether navigation is allowed. Using
`parameters.postId=missing` produces `"statusCode": 302` and
`"location": "/not-found"`.

For the search route, use `routeId=Index` and `queryString.q=kotlin`.

## Frontend components

`spring/frontend/src/components` contains the layout, searchable post list,
post reader, shared create/edit form, and message page. Loading, empty, error,
and saving states are supplied through props; form editing state stays local.
Links use the generated `BlogRoutes` builders. API loading, mutations, router
setup, and bundling are not connected yet.

To type-check the components, use Node with npm and generate the client routes
first. All frontend dependencies belong to the example.

```sh
./gradlew :examples:spring:generateClientRoutes
npm --prefix examples/spring/frontend ci
npm --prefix examples/spring/frontend run typecheck
```

The published `@spa-kit/react-router` 0.1.0 still expects parsers to return
`{ params, query }` and sends `queryParameters.*` to the decision endpoint.
This checkout uses `{ params, queryString }` and `queryString.*`. Before router
integration, spa-kit needs to adopt that contract or the example needs an
adapter. The components use React Router 7, matching spa-kit's current
development version.

## Build

```sh
./gradlew :examples:build
```

The repository-wide `./gradlew build` also builds these projects.
The example projects have no publishing configuration.
