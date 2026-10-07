import { WikiRoutes } from "./WikiRoutes";
import type { EditContext, IndexContext, ViewContext } from "./WikiRoutes";
import type { IndexContext as OtherIndexContext } from "./ConfigTestRoutes";
import type { DocumentDetailContext } from "./ClientGeneratorTestRoutes";
import type { FiltersContext, QueryStringContext, QueryStringKeyContext, SearchContext, UserDetailContext } from "./QueryTestRoutes";

type Equal<Left, Right> =
  (<T>() => T extends Left ? 1 : 2) extends (<T>() => T extends Right ? 1 : 2) ? true : false;
declare function exact<Value extends true>(): void;

function verifyContextTypes(): void {
  exact<Equal<ViewContext, NonNullable<ReturnType<typeof WikiRoutes.View.parse>>>>();
  exact<Equal<ViewContext["params"], { wikiId: string }>>();
  exact<Equal<ViewContext["queryString"], { tab?: string }>>();
  exact<Equal<IndexContext["params"], {}>>();
  exact<Equal<IndexContext["queryString"], {}>>();
  exact<Equal<DocumentDetailContext["params"], { id: string; tab?: string }>>();
  exact<Equal<SearchContext["queryString"], { q: string }>>();
  exact<Equal<FiltersContext["queryString"]["tag"], readonly string[]>>();
  exact<Equal<UserDetailContext["queryString"]["tag"], readonly string[] | undefined>>();

  const loadView = ({ params }: ViewContext) => params.wikiId;
  const correct: (context: ViewContext) => string = loadView;
  // Same path shape and an optional query field would be structurally compatible without identity.
  // @ts-expect-error Another route's context is rejected.
  const wrongRoute: (context: EditContext) => string = loadView;

  exact<Equal<QueryStringContext["params"], QueryStringKeyContext["params"]>>();
  exact<Equal<QueryStringContext["queryString"], QueryStringKeyContext["queryString"]>>();
  const loadQueryString = ({ queryString }: QueryStringContext) => queryString.q;
  // @ts-expect-error Different routes remain distinct even when both value shapes are identical.
  const sameShapeWrongRoute: (context: QueryStringKeyContext) => string = loadQueryString;

  exact<Equal<IndexContext["params"], OtherIndexContext["params"]>>();
  exact<Equal<IndexContext["queryString"], OtherIndexContext["queryString"]>>();
  const loadIndex = (_context: IndexContext) => "index";
  // @ts-expect-error Identical route names and value shapes from another application are rejected.
  const wrongApplication: (context: OtherIndexContext) => string = loadIndex;
}

// Exporting an inferred parser result must also support declaration emission.
export const context = WikiRoutes.View.parse({ wikiId: "42" }, new URLSearchParams("tab=history"));
if (context === null) {
  throw new Error("Expected a valid context");
}
if (JSON.stringify(context) !== '{"params":{"wikiId":"42"},"queryString":{"tab":"history"}}') {
  throw new Error("Context aliases must not change parsed values");
}
if (Reflect.ownKeys(context).join(",") !== "params,queryString") {
  throw new Error("Context identity must not add runtime properties");
}
console.log("route contexts verified");
