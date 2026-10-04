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

Application rules are a deny-by-default gate, followed by an optional typed
route access handler. Any config, test, or example needs an application-level
`Allow` — e.g. the `AllowAll` builtin, or `RecordingRule(RouteRuleResult.Allow)`
in tests — for a route to be served. Routes with `generateAccessHandler = true`
also require a registered handler returning `RouteDecision.Allow` or `Redirect`.
Per-route `routeRules` have been removed.

User-facing runtime docs live in [docs/runtime.md](docs/runtime.md) and
[docs/spring-boot-client-apps.md](docs/spring-boot-client-apps.md).
