import { WikiRoutes } from "./WikiRoutes";
import { QueryTestRoutes } from "./QueryTestRoutes";
import { ClientGeneratorTestRoutes } from "./ClientGeneratorTestRoutes";

// A consumer-owned router can infer renderer arguments from the generated parser.
// This declaration tests the contract without implementing or depending on a router.
type Route = {
  path: string;
  parse(params: Readonly<Record<string, string | undefined>>, search: URLSearchParams):
    { params: object; query: object } | null;
};
type Parsed<T extends Route> = NonNullable<ReturnType<T["parse"]>>;
declare function createSpaRouter<TRoutes extends Record<string, Route>>(
  routes: TRoutes,
  renderers: { [K in keyof NoInfer<TRoutes>]: {
    render: (params: Parsed<TRoutes[K]>["params"], query: Parsed<TRoutes[K]>["query"]) => unknown;
  } },
): unknown;

function verifyRendererTypes(): void {
  createSpaRouter(WikiRoutes, {
    Index: {
      render: () => "index",
    },
    View: {
      render: (params, query) => {
        const wikiId: string = params.wikiId;
        const tab: string | undefined = query.tab;
        // @ts-expect-error Only declared path parameters are available.
        params.unknown;
        // @ts-expect-error Only declared query parameters are available.
        query.unknown;
        // @ts-expect-error An optional query is not guaranteed to be present.
        const requiredTab: string = query.tab;
        return { wikiId, tab };
      },
    },
    Edit: {
      render: (params) => {
        const wikiId: string = params.wikiId;
        return wikiId;
      },
    },
  });
  // @ts-expect-error Every declared route requires a renderer.
  createSpaRouter(WikiRoutes, { Index: { render: () => "index" } });
  createSpaRouter(WikiRoutes, {
    Index: { render: () => "index" },
    View: { render: () => "view" },
    Edit: { render: () => "edit" },
    // @ts-expect-error Unknown route keys are rejected.
    Unknown: { render: () => "unknown" },
  });
  createSpaRouter(QueryTestRoutes, {
    Home: { render: (params, query) => {
      // @ts-expect-error Routes without path declarations have no typed path values.
      params.id;
      // @ts-expect-error Routes without query declarations have no typed query values.
      query.q;
    } },
    Query: { render: (_, query) => { const q: string = query.q; return q; } },
    QueryKey: { render: () => null },
    UserDetail: { render: (params, query) => {
      const id: string = params.id;
      const foo: string = query.foo;
      const tags: readonly string[] | undefined = query.tag;
      // @ts-expect-error Repeated query values are lists, not scalars.
      const tag: string = query.tag;
      return { id, foo, tags };
    } },
    Search: { render: () => null },
    Filters: { render: (_, query) => { const tags: readonly string[] = query.tag; return tags; } },
    OptionalQuery: { render: () => null },
    OptionalMixed: { render: () => null },
    SameName: { render: () => null },
    Special: { render: () => null },
  });
}

function equal(actual: unknown, expected: unknown): void {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(JSON.stringify({ actual, expected }));
  }
}

equal(WikiRoutes.Index.path, "/wiki");
equal(WikiRoutes.View.path, "/wiki/:wikiId");
equal(WikiRoutes.Edit.path, "/wiki/:wikiId/edit");
equal(WikiRoutes.Index.parse({}, new URLSearchParams()), { params: {}, query: {} });
equal(WikiRoutes.Edit.parse({ wikiId: "42" }, new URLSearchParams()), {
  params: { wikiId: "42" }, query: {},
});
// React Router supplies decoded path values; do not decode them twice.
equal(WikiRoutes.View.parse({ wikiId: "雪%2F" }, new URLSearchParams("tab=a+b%2B%26%E9%9B%AA&extra=ignored")), {
  params: { wikiId: "雪%2F" }, query: { tab: "a b+&雪" },
});
equal(WikiRoutes.View.parse({ wikiId: "42", parentId: "ignored" }, new URLSearchParams()), {
  params: { wikiId: "42" }, query: {},
});
equal(WikiRoutes.View.parse({}, new URLSearchParams()), null);
equal(WikiRoutes.Edit.parse({ wikiId: undefined }, new URLSearchParams()), null);
equal(WikiRoutes.View.parse({ wikiId: "42" }, new URLSearchParams("tab=a&tab=b")), null);
equal(QueryTestRoutes.Search.parse({}, new URLSearchParams()), null);
equal(QueryTestRoutes.Search.parse({}, new URLSearchParams("q=")), { params: {}, query: { q: "" } });
equal(QueryTestRoutes.Filters.parse({}, new URLSearchParams()), null);
equal(QueryTestRoutes.Filters.parse({}, new URLSearchParams("tag=one&tag=&tag=three")), {
  params: {}, query: { tag: ["one", "", "three"] },
});
equal(QueryTestRoutes.OptionalQuery.parse({}, new URLSearchParams()), { params: {}, query: {} });
equal(ClientGeneratorTestRoutes.DocumentDetail.parse({ id: "42" }, new URLSearchParams()), {
  params: { id: "42" }, query: {},
});
const special = QueryTestRoutes.Special.parse({}, new URLSearchParams("a+b=x&__proto__=literal&class="));
equal(special?.query["__proto__"], "literal");
equal(special?.query["a b"], ["x"]);
equal(special?.query.class, "");
console.log("route objects verified");
