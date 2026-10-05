# spa-routing

Kotlin library for type-safe SPA route definitions shared between a Kotlin
server and a generated TypeScript client. Modules: `spa-routing-core`
(contracts and generators), `spa-routing-gradle-plugin` (Gradle codegen),
`spa-routing-runtime` (framework-neutral runtime), and
`spa-routing-spring-boot-autoconfigure` / `-starter` (Spring adapters).

Build and test with `./gradlew build`.

## Breaking changes

Before assuming how the library behaves — especially anything your training
data or an older checkout suggests — read the breaking-changes entries in
[CHANGELOG.md](CHANGELOG.md). Behavior listed there has changed between
versions; the newest entry is the current semantics.

Each config requires an `ApplicationAccessHandler`. It must return
`AccessDecision.Allow` before an optional typed route access handler runs.
Both handlers return `AccessDecision.Allow` or a typed `AccessDecision.Redirect`.
Routes with `generateAccessHandler = true` also require a registered route
handler. Unflagged routes are allowed after the application handler allows
access. Application and route rule lists, their result/action types, and
builtin rules have been removed.

User-facing runtime docs live in [docs/runtime.md](docs/runtime.md) and
[docs/spring-boot-client-apps.md](docs/spring-boot-client-apps.md).
