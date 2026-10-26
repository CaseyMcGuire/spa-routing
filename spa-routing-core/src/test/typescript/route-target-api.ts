import { WikiRoutes, type RouteTarget as WikiTarget } from "./WikiRoutes";
import { QueryTestRoutes, type RouteTarget as QueryTarget } from "./QueryTestRoutes";
import { ClientGeneratorTestRoutes } from "./ClientGeneratorTestRoutes";
import { ConfigTestRoutes } from "./ConfigTestRoutes";
import { AccessTestRoutes } from "./AccessTestRoutes";
import type { RouteTarget } from "./route-runtime";

type Equal<Left, Right> =
  (<T>() => T extends Left ? 1 : 2) extends (<T>() => T extends Right ? 1 : 2) ? true : false;
declare function exact<Value extends true>(): void;

function checkTarget(target: RouteTarget, href: string, applicationId: string, routeId: string): void {
  const expected = { href, applicationId, routeId };
  if (JSON.stringify(target) !== JSON.stringify(expected)) {
    throw new Error(JSON.stringify({ target, expected }));
  }
  if (Object.getPrototypeOf(target) !== Object.prototype
      || Reflect.ownKeys(target).join(",") !== "href,applicationId,routeId"
      || typeof Object.getOwnPropertyDescriptor(target, "href")?.value !== "string") {
    throw new Error("Targets must be plain objects with an eagerly computed href");
  }
}

// Every builder variant has the same concrete target shape, including across applications.
const cases: readonly [RouteTarget, string, string, string][] = [
  [ConfigTestRoutes.Index(), "/", "configtest", "Index"],
  [WikiRoutes.Index(), "/wiki", "wiki", "Index"],
  [WikiRoutes.Edit({ wikiId: "a b/雪" }), "/wiki/a%20b%2F%E9%9B%AA/edit", "wiki", "Edit"],
  [ClientGeneratorTestRoutes.DocumentDetail({ id: "42" }), "/clientgeneratortest/documents/42/", "clientgeneratortest", "DocumentDetail"],
  [ClientGeneratorTestRoutes.DocumentDetail({ id: "42", tab: "a b" }), "/clientgeneratortest/documents/42/a%20b", "clientgeneratortest", "DocumentDetail"],
  [AccessTestRoutes.Optional({}), "/access/optional/", "accesstest", "Optional"],
  [AccessTestRoutes.Optional({ id: "a/b" }), "/access/optional/a%2Fb", "accesstest", "Optional"],
  [QueryTestRoutes.Search({ q: "a b+&雪" }), "/querytest/search?q=a+b%2B%26%E9%9B%AA", "querytest", "Search"],
  [QueryTestRoutes.Search({ q: "" }), "/querytest/search?q=", "querytest", "Search"],
  [QueryTestRoutes.Filters({ tag: ["a b", "雪", ""] }), "/querytest/filters?tag=a+b&tag=%E9%9B%AA&tag=", "querytest", "Filters"],
  [QueryTestRoutes.OptionalQuery(), "/querytest/optional", "querytest", "OptionalQuery"],
  [QueryTestRoutes.OptionalQuery({ tag: [] }), "/querytest/optional", "querytest", "OptionalQuery"],
  [QueryTestRoutes.OptionalQuery({ foo: "", tag: ["a", "b"] }), "/querytest/optional?foo=&tag=a&tag=b", "querytest", "OptionalQuery"],
  [QueryTestRoutes.OptionalMixed({ id: "42" }), "/querytest/optional/42", "querytest", "OptionalMixed"],
  [QueryTestRoutes.OptionalMixed({ id: "a b" }, { foo: "a b" }), "/querytest/optional/a%20b?foo=a+b", "querytest", "OptionalMixed"],
  [QueryTestRoutes.UserDetail({ id: "x/y" }, { foo: "a b", baz: "", tag: ["x/y", "é"] }), "/querytest/users/x%2Fy?foo=a+b&baz=&tag=x%2Fy&tag=%C3%A9", "querytest", "UserDetail"],
  [AccessTestRoutes.Post({ postId: "42" }, { q: "one", tag: ["two"] }), "/access/posts/42?q=one&tag=two", "accesstest", "Post"],
];
for (const [target, href, applicationId, routeId] of cases) {
  checkTarget(target, href, applicationId, routeId);
}

// Later changes to the caller's inputs cannot change an already constructed destination.
const params = { id: "42" };
const query = { foo: "first", tag: ["one"] };
export const destination = QueryTestRoutes.UserDetail(params, query);
params.id = "99";
query.foo = "second";
query.tag.push("two");
checkTarget(destination, "/querytest/users/42?foo=first&tag=one", "querytest", "UserDetail");
checkTarget(QueryTestRoutes.UserDetail(params, query), "/querytest/users/99?foo=second&tag=one&tag=two", "querytest", "UserDetail");

if (false) {
  exact<Equal<RouteTarget, { readonly href: string; readonly applicationId: string; readonly routeId: string }>>();
  exact<Equal<WikiTarget, RouteTarget>>();
  exact<Equal<QueryTarget, RouteTarget>>();
  exact<Equal<ReturnType<typeof WikiRoutes.Index>["applicationId"], "wiki">>();
  exact<Equal<ReturnType<typeof WikiRoutes.Index>["routeId"], "Index">>();
  exact<Equal<ReturnType<typeof WikiRoutes.Edit>["routeId"], "Edit">>();
  exact<Equal<ReturnType<typeof ClientGeneratorTestRoutes.DocumentDetail>["routeId"], "DocumentDetail">>();
  exact<Equal<ReturnType<typeof AccessTestRoutes.Optional>["routeId"], "Optional">>();
  exact<Equal<ReturnType<typeof QueryTestRoutes.Search>["applicationId"], "querytest">>();
  exact<Equal<ReturnType<typeof QueryTestRoutes.Search>["routeId"], "Search">>();
  exact<Equal<ReturnType<typeof QueryTestRoutes.Filters>["routeId"], "Filters">>();
  exact<Equal<ReturnType<typeof QueryTestRoutes.OptionalQuery>["routeId"], "OptionalQuery">>();
  exact<Equal<ReturnType<typeof QueryTestRoutes.OptionalMixed>["routeId"], "OptionalMixed">>();
  exact<Equal<typeof destination.applicationId, "querytest">>();
  exact<Equal<typeof destination.routeId, "UserDetail">>();
  exact<Equal<typeof destination.href, string>>();

  exact<Equal<Parameters<typeof WikiRoutes.Index>, []>>();
  exact<Equal<Parameters<typeof WikiRoutes.Edit>, [{ wikiId: string }]>>();
  exact<Equal<Parameters<typeof ClientGeneratorTestRoutes.DocumentDetail>, [{ id: string; tab?: string }]>>();
  exact<Equal<Parameters<typeof AccessTestRoutes.Optional>, [{ id?: string }]>>();
  exact<Equal<Parameters<typeof QueryTestRoutes.Search>, [{ q: string }]>>();
  exact<Equal<Parameters<typeof QueryTestRoutes.Filters>, [{ tag: readonly string[] }]>>();
  exact<Equal<Parameters<typeof QueryTestRoutes.OptionalQuery>, [queryString?: { foo?: string; tag?: readonly string[] }]>>();
  exact<Equal<Parameters<typeof QueryTestRoutes.OptionalMixed>, [params: { id: string }, queryString?: { foo?: string }]>>();
  exact<Equal<Parameters<typeof QueryTestRoutes.UserDetail>, [params: { id: string }, queryString: { foo: string; baz?: string; tag?: readonly string[] }]>>();

  // @ts-expect-error String consumers must explicitly use href.
  const url: string = WikiRoutes.Index();
  // @ts-expect-error href is a computed string, not a URL-building method.
  destination.href();
  // @ts-expect-error Target properties are readonly.
  destination.href = "/changed";
  // @ts-expect-error Target properties are readonly.
  destination.applicationId = "querytest";
  // @ts-expect-error Target properties are readonly.
  destination.routeId = "UserDetail";
  // @ts-expect-error Route-definition metadata belongs to the builder.
  destination.hasAccessHandler;
  // @ts-expect-error No-argument routes do not gain parameters.
  WikiRoutes.Index({ id: "42" });
  // @ts-expect-error Required path arguments cannot be omitted.
  WikiRoutes.Edit();
  // @ts-expect-error Required path fields cannot be omitted.
  WikiRoutes.Edit({});
  // @ts-expect-error Path values must be strings.
  WikiRoutes.Edit({ wikiId: 42 });
  // @ts-expect-error Undeclared path keys remain invalid.
  WikiRoutes.Edit({ id: "42" });
  // @ts-expect-error Optional path values still require the existing params object.
  AccessTestRoutes.Optional();
  // @ts-expect-error Optional path values must be strings when present.
  ClientGeneratorTestRoutes.DocumentDetail({ id: "42", tab: 1 });
  // @ts-expect-error Query-only routes do not accept a separate path argument.
  QueryTestRoutes.Search({}, { q: "term" });
  // @ts-expect-error Required query arguments remain required.
  QueryTestRoutes.Search();
  // @ts-expect-error Optional query fields must have the declared types.
  QueryTestRoutes.OptionalQuery({ foo: ["one"] });
  // @ts-expect-error Repeated query values must be string lists.
  QueryTestRoutes.Filters({ tag: [42] });
}

console.log("route targets verified");
