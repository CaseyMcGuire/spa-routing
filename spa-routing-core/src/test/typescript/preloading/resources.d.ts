// Structural test double for a typed resource producer such as Relay loadQuery.
// This does not test Relay, network requests, disposal, or React rendering.
interface Operation {
  variables: object;
  response: object;
}

export interface ViewQuery extends Operation {
  variables: { wikiId: string };
  response: { title: string; body: string };
}

export interface EditQuery extends Operation {
  variables: { wikiId: string; draft: string };
  response: { draftBody: string; canPublish: boolean };
}

export interface PreloadedQuery<Query extends Operation> {
  readonly variables: Query["variables"];
  readonly data: Query["response"];
  dispose(): void;
}

interface QueryDocument<Query extends Operation> {
  readonly operation: Query;
}

export declare const environment: unique symbol;
export declare const ViewWikiPageQuery: QueryDocument<ViewQuery>;
export declare const EditWikiPageQuery: QueryDocument<EditQuery>;

export declare function loadQuery<Query extends Operation>(
  queryEnvironment: typeof environment,
  query: QueryDocument<Query>,
  variables: NoInfer<Query["variables"]>,
): PreloadedQuery<Query>;

export declare function ViewWikiPage(props: { queryRef: PreloadedQuery<ViewQuery> }): null;
export declare function EditWikiPage(props: { queryRef: PreloadedQuery<EditQuery> }): null;
export declare function AboutPage(): null;

export type Equal<Left, Right> =
  (<T>() => T extends Left ? 1 : 2) extends (<T>() => T extends Right ? 1 : 2) ? true : false;
export type IsAny<T> = 0 extends (1 & T) ? true : false;
export declare function expectTrue<T extends true>(): void;
