import { WikiRoutes } from "./WikiRoutes";
import { QueryTestRoutes } from "./QueryTestRoutes";
import { ClientGeneratorTestRoutes } from "./ClientGeneratorTestRoutes";

// A consumer-owned router can infer renderer arguments from the generated parser.
// This declaration tests the contract without implementing or depending on a router.
type Route = {
  path: string;
  parse(params: Readonly<Record<string, string | undefined>>, search: URLSearchParams):
    { params: object; queryString: object } | null;
};
type Parsed<T extends Route> = NonNullable<ReturnType<T["parse"]>>;
declare function createSpaRouter<TRoutes extends Record<string, Route>>(
  routes: TRoutes,
  renderers: { [K in keyof NoInfer<TRoutes>]: {
    render: (params: Parsed<TRoutes[K]>["params"], queryString: Parsed<TRoutes[K]>["queryString"]) => unknown;
  } },
): unknown;

function verifyRendererTypes(): void {
  createSpaRouter(WikiRoutes, {
    Index: {
      render: () => "index",
    },
    View: {
      render: (params, queryString) => {
        const wikiId: string = params.wikiId;
        const tab: string | undefined = queryString.tab;
        // @ts-expect-error Only declared path parameters are available.
        params.unknown;
        // @ts-expect-error Only declared query parameters are available.
        queryString.unknown;
        // @ts-expect-error An optional query is not guaranteed to be present.
        const requiredTab: string = queryString.tab;
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
    Home: { render: (params, queryString) => {
      // @ts-expect-error Routes without path declarations have no typed path values.
      params.id;
      // @ts-expect-error Routes without query declarations have no typed query values.
      queryString.q;
    } },
    QueryString: { render: (_, queryString) => { const q: string = queryString.q; return q; } },
    QueryStringKey: { render: () => null },
    UserDetail: { render: (params, queryString) => {
      const id: string = params.id;
      const foo: string = queryString.foo;
      const tags: readonly string[] | undefined = queryString.tag;
      // @ts-expect-error Repeated query values are lists, not scalars.
      const tag: string = queryString.tag;
      return { id, foo, tags };
    } },
    Search: { render: () => null },
    Filters: { render: (_, queryString) => { const tags: readonly string[] = queryString.tag; return tags; } },
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
equal(WikiRoutes.Index.parse({}, new URLSearchParams()), { params: {}, queryString: {} });
equal(WikiRoutes.Edit.parse({ wikiId: "42" }, new URLSearchParams()), {
  params: { wikiId: "42" }, queryString: {},
});
// React Router supplies decoded path values; do not decode them twice.
equal(WikiRoutes.View.parse({ wikiId: "雪%2F" }, new URLSearchParams("tab=a+b%2B%26%E9%9B%AA&extra=ignored")), {
  params: { wikiId: "雪%2F" }, queryString: { tab: "a b+&雪" },
});
equal(WikiRoutes.View.parse({ wikiId: "42", parentId: "ignored" }, new URLSearchParams()), {
  params: { wikiId: "42" }, queryString: {},
});
equal(WikiRoutes.View.parse({}, new URLSearchParams()), null);
equal(WikiRoutes.Edit.parse({ wikiId: undefined }, new URLSearchParams()), null);
equal(WikiRoutes.View.parse({ wikiId: "42" }, new URLSearchParams("tab=a&tab=b")), null);
equal(QueryTestRoutes.Search.parse({}, new URLSearchParams()), null);
equal(QueryTestRoutes.Search.parse({}, new URLSearchParams("q=")), { params: {}, queryString: { q: "" } });
equal(QueryTestRoutes.Filters.parse({}, new URLSearchParams()), null);
equal(QueryTestRoutes.Filters.parse({}, new URLSearchParams("tag=one&tag=&tag=three")), {
  params: {}, queryString: { tag: ["one", "", "three"] },
});
equal(QueryTestRoutes.OptionalQuery.parse({}, new URLSearchParams()), { params: {}, queryString: {} });
equal(ClientGeneratorTestRoutes.DocumentDetail.parse({ id: "42" }, new URLSearchParams()), {
  params: { id: "42" }, queryString: {},
});
const special = QueryTestRoutes.Special.parse({}, new URLSearchParams("a+b=x&__proto__=literal&class="));
equal(special?.queryString["__proto__"], "literal");
equal(special?.queryString["a b"], ["x"]);
equal(special?.queryString.class, "");
console.log("route objects verified");
