# Typed route preload compiler fixtures

## Selected API: generated input context aliases

Core now emits `ViewContext`, `EditContext`, and one alias for every other route.
Each alias comes from that route's parser, including its opaque application/route
identity. The selected API keeps plain configuration objects and annotates only
preload inputs:

```tsx
import { WikiRoutes } from "./generated/WikiRoutes";
import type { ViewContext } from "./generated/WikiRoutes";
import { createSpaRouter } from "@spa-kit/react-router";

createSpaRouter(WikiRoutes, {
  View: {
    render: ({ preload }) => <ViewWikiPage queryRef={preload} />,
    preload: ({ params }: ViewContext) =>
      loadQuery(environment, ViewWikiPageQuery, { wikiId: params.wikiId }),
  },
  // Other route entries omitted here.
});
```

[annotated-contexts.tsx](annotated-contexts.tsx) checks this against the generic
mapped signature in [mapped-router.d.ts](mapped-router.d.ts). It proves both
property orders, independent resource types, exact inputs, wrong-context and
wrong-resource rejection, and absence of preload data on ordinary routes. A
zero-parameter preload needs no annotation. No application adapter is generated.

The fixture also pins the missing-annotation failure: this mapped signature loses
the preload property in the render context. The older experimental mapped
signature below instead leaves its type as `unknown`. Neither is a useful primary
diagnostic. A targeted consumer-enabled lint rule should require explicit context
annotations at preload parameters; strict TypeScript settings and brands alone
do not enforce annotation syntax.

The shared library must preserve the complete parser return type as its context;
`Pick<ParsedRoute, "params" | "queryString">` erases the identity. Runtime navigation
and cleanup remain in `@spa-kit/react-router`. These fixtures use resource doubles
and do not implement or test that runtime.

## Earlier alternative: application-specific signatures

**Result:** an application-specific signature with one independent result generic
per concrete route passes the inference requirements on TypeScript **5.9.3**.
These hand-written candidate adapter declarations remain as regression controls,
compiled against generated routes. They are not the selected public API.

This alternative changes the import:

```tsx
import { WikiRoutes } from "./generated/WikiRoutes";
import { createSpaRouter } from "./generated/WikiRouter"; // optional adapter output

createSpaRouter(WikiRoutes, {
  View: {
    preload: ({ params }) =>
      loadQuery(environment, ViewWikiPageQuery, { wikiId: params.wikiId }),
    render: ({ preload }) => <ViewWikiPage queryRef={preload} />,
  },
  // Each other generated route has a plain configuration object too.
});
```

The concrete signature is in [WikiRouter.d.ts](WikiRouter.d.ts). Both this call
shape and `createWikiRouter(config)` pass the tests. The latter can bind the
application's routes inside the adapter, removing the routes argument altogether.
Neither API needs per-route wrappers, input annotations, or preload generics.

## Why it works

The application's configuration has explicit `View`, `Edit`, `About`, and `Section`
properties, with separate generic parameters. TypeScript can infer each producer
before typing its sibling consumer. Replacing those properties with a reverse
mapped type reproduces [TypeScript #53018](https://github.com/microsoft/TypeScript/issues/53018),
as checked by [limitations.tsx](limitations.tsx).

[adapter-contract.d.ts](adapter-contract.d.ts) prototypes shared types that would
belong in `@spa-kit/react-router`:

- Context comes from `NonNullable<ReturnType<Route["parse"]>>`; parameter and query
  schemas are not duplicated in the adapter.
- A private unique-symbol default represents an absent preload. A callback that
  returns `undefined`, `void`, `null`, or `never` still gets a `preload` property
  with precisely that type. `never` needs an explicit check because it is a
  subtype of every type, including the absence marker.
- `NoInfer` prevents render from supplying a result type. A conditional required
  callback prevents a possibly undefined function from promising a resource.
- The render context contains only `params` and `queryString` when preload is
  omitted. Making the entire entry a union of loaded/unloaded configurations lost
  contextual typing in an initial compiler experiment.

## Evidence

Run the automated prototype against freshly generated routes:

```sh
./gradlew :spa-routing-core:test --tests '*GeneratedQueryApiTest.prototype application signatures infer independent route preloads'
```

The harness invokes `tsc --strict --noEmit` twice, with
`exactOptionalPropertyTypes` on and off. The normal `./gradlew build` includes it.

| Check | Evidence |
| --- | --- |
| Inline preload inputs come from generated parsers | Exact type assertions for both callbacks in `inference.tsx` |
| Each resource remains independent | View, Edit, and Section return three distinct shapes; exact equality and `IsAny` checks |
| Wrong resource types fail | `@ts-expect-error` on both directions of incompatible JSX query references |
| No preload property when omitted | Exact context/key assertions and rejected property access |
| Precise path and query types | Required/optional strings, required/optional readonly arrays, wrong keys and value types |
| Optional callbacks are safe | Possibly absent callbacks and fabricated render resources are rejected |
| Other result types stay exact | Method syntax, primitives, literal objects, `null`, `undefined`, `void`, `never`, and promises |
| Route metadata remains available | Existing callable builders and `hasAccessHandler` compile without alteration |
| Configuration mistakes fail | Missing/unknown route entries and incompatible preload parameters |

Every negative assertion uses `@ts-expect-error`, which itself fails if TypeScript
starts accepting that line. Positive exact-type assertions fail for a shared union,
`unknown`, or `any`. The tests use a typed `loadQuery`/resource **test double** and
JSX components with incompatible props. They do not depend on or exercise Relay.
The router preserves the producer's return type; it cannot recover type information
already lost inside `loadQuery`, or distinguish structurally identical resources.
Verify the application's actual Relay/document typings at integration time.
Router and render-result types are opaque placeholders; native React Router option
composition and actual `ReactNode` compatibility remain integration work.

## Constraints and integration

1. **Put `preload` before `render`.** Reversing the properties fails inference,
   even with concrete generics. `limitations.tsx` captures this compiler behavior.
   The requested object order works. Hoisting an untyped configuration object out
   of the call also loses the call's contextual typing; keep it inline.
2. **Use an optional application adapter.** Its signature imports shared types from
   spa-kit and delegates to spa-kit's preload-aware runtime. That runtime still
   needs to implement navigation, concurrent preparation, ownership, and cleanup.
   No runtime implementation or type assertion hiding an implementation mismatch
   is included in this experiment. Current spa-kit 0.2.0 cannot execute this API.
3. **Avoid global augmentation for now.** A separate probe against the installed
   0.2.0 declarations proves inference for one augmented overload, but also shows
   changed contextual typing of existing calls and implicit `any` for another
   application's parser shape. Multiple application overloads need a reliable
   discriminator and regression tests before retaining the package import itself.
   This does not prove that a future library-level API is impossible.
4. **No framework dependencies in generated definitions.** An optional adapter
   would be separate from the `WikiRoutes` module; only that adapter would import
   spa-kit types/runtime. No React,
   React Router, or Relay dependency was added to core or its generated modules.
5. **Promises are preserved.** The test asserts the exact promise return type;
   deciding to await it would be a separate runtime/API decision.

The separate augmentation probe uses the example's already installed dependencies:

```sh
node examples/frontend/node_modules/typescript/bin/tsc \
  -p spa-routing-core/src/test/typescript/preloading/integration/tsconfig.json
```

This probe is deliberately separate from the core build, so core tests do not
depend on the example's React packages. Its expected errors document the overload
hazards; a successful compiler run does not mean augmentation is recommended.

An additional experiment with `noUncheckedIndexedAccess` found an existing
generated-parser error: `values[0]` is possibly undefined when assigned to the
query-string map. This flag is not enabled in the repo's frontend configuration.
The generator was left unchanged; support for that flag is outside this prototype.

The optional application adapter is retained only for comparison. The chosen
implementation adds context aliases and consumer lint enforcement instead.
