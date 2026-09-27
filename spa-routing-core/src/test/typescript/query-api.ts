import {
  QueryTestRoutes as routes, UserDetailQueryStringKey, SearchQueryStringKey, SpecialQueryStringKey,
} from "./QueryTestRoutes";

function equal(actual: unknown, expected: unknown): void {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(JSON.stringify({ actual, expected }));
  }
}

function throws(action: () => unknown): void {
  let caught = false;
  try { action(); } catch { caught = true; }
  if (!caught) throw new Error("Expected the builder to reject invalid queryString values");
}

equal(routes.Home(), "/querytest/home");
equal(routes.Search({ q: "term" }), "/querytest/search?q=term");
equal(routes.Filters({ tag: ["one", "two"] as const }), "/querytest/filters?tag=one&tag=two");
equal(routes.OptionalQuery(), "/querytest/optional");
equal(routes.OptionalQuery({ tag: [] }), "/querytest/optional");
equal(routes.OptionalMixed({ id: "42" }), "/querytest/optional/42");
equal(routes.SameName({ queryString: "path", queryString_: "path2" }, { queryString: "wire" }), "/querytest/same/path/path2?queryString=wire");
equal(routes.UserDetail.path, "/querytest/users/:id");
equal(routes.UserDetail.applicationId, "querytest");
equal(routes.UserDetail.routeId, "UserDetail");
throws(() => routes.Filters({ tag: [] }));
// Simulate untyped JavaScript callers as well as checking the static API below.
throws(() => (routes.Search as Function)({}));
throws(() => (routes.Search as Function)({ q: ["a", "b"] }));
throws(() => (routes.Filters as Function)({ tag: "a" }));
throws(() => (routes.Filters as Function)({ tag: [1] }));

const special = routes.Special({ "a b": ["x+y&=?#/", "é雪😀~*"], class: "", ["__proto__"]: "literal", "a\"$\n": "escaped" });
equal(special, "/querytest/special?a+b=x%2By%26%3D%3F%23%2F&a+b=%C3%A9%E9%9B%AA%F0%9F%98%80%7E*&class=&__proto__=literal&a%22%24%0A=escaped");
equal(SpecialQueryStringKey.A_B, "a b");
const specialQuery = routes.Special.queryString(new URLSearchParams(special.split("?")[1]));
equal(specialQuery[SpecialQueryStringKey.__PROTO__], ["literal"]);

const raw = new URLSearchParams("foo=&tag=a&tag=b&utm_source=extra");
const queryString = routes.UserDetail.queryString(raw);
equal(queryString[UserDetailQueryStringKey.FOO], [""]);
equal(queryString[UserDetailQueryStringKey.TAG], ["a", "b"]);
equal(queryString[UserDetailQueryStringKey.BAZ], undefined);
equal(Object.keys(queryString), ["foo", "tag"]);
equal(raw.get("utm_source"), "extra");
equal(Object.isFrozen(queryString), true);
equal(Object.isFrozen(queryString[UserDetailQueryStringKey.TAG]), true);

if (false) {
  // @ts-expect-error Required query argument cannot be omitted.
  routes.UserDetail({ id: "123" });
  // @ts-expect-error Required query key cannot be omitted.
  routes.UserDetail({ id: "123" }, {});
  // @ts-expect-error Unknown query keys are not builder arguments.
  routes.UserDetail({ id: "123" }, { foo: "x", unknown: "x" });
  // @ts-expect-error Scalar values must be strings.
  routes.UserDetail({ id: "123" }, { foo: ["x"] });
  // @ts-expect-error Repeated values must be lists.
  routes.Filters({ tag: "x" });
  // @ts-expect-error Required list cannot be omitted.
  routes.Filters({});
  // @ts-expect-error Routes without path parameters still require their query argument.
  routes.Search();
  // @ts-expect-error Enums are scoped to a route's declared keys.
  queryString[SearchQueryStringKey.Q];
  // @ts-expect-error Unknown enum keys are not generated.
  UserDetailQueryStringKey.UNKNOWN;
  // @ts-expect-error Enum map views cannot be mutated.
  queryString[UserDetailQueryStringKey.FOO] = ["changed"];
}

console.log(routes.UserDetail({ id: "123" }, { foo: "a b+&=雪", baz: "", tag: ["x/y", "é"] }));
