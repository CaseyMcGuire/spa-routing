export type BlogPost = {
  id: string;
  title: string;
  body: string;
};

export type WritePostRequest = Pick<BlogPost, "title" | "body">;

export type LoadState<T> =
  | { type: "loading" }
  | { type: "error"; message: string }
  | { type: "ready"; value: T };
