import { useState } from "react";
import { Link } from "react-router";
import type { BlogPost, LoadState } from "../blog";
import { BlogRoutes } from "../routes";

type Props = {
  posts: LoadState<readonly BlogPost[]>;
  query: string;
  onSearch: (query: string) => void;
};

type SearchProps = Pick<Props, "query" | "onSearch">;
type ContentProps = Pick<Props, "posts" | "query">;

const styles = {
  search: { display: "flex", gap: "0.5rem", flexWrap: "wrap" as const, alignItems: "center" },
  post: { padding: "1rem 0", borderBottom: "1px solid #ddd" },
};

export default function PostList(props: Props) {
  return (
    <section>
      <h2>Posts</h2>
      <SearchForm key={props.query} query={props.query} onSearch={props.onSearch} />
      <PostListContent posts={props.posts} query={props.query} />
    </section>
  );
}

function SearchForm(props: SearchProps) {
  const [query, setQuery] = useState(props.query);

  return (
    <form
      role="search"
      style={styles.search}
      onSubmit={(event) => {
        event.preventDefault();
        props.onSearch(query.trim());
      }}
    >
      <label htmlFor="post-search">Search posts</label>
      <input id="post-search" type="search" value={query} onChange={(event) => setQuery(event.target.value)} />
      <button type="submit">Search</button>
    </form>
  );
}

function PostListContent(props: ContentProps) {
  switch (props.posts.type) {
    case "loading":
      return <p role="status">Loading posts…</p>;
    case "error":
      return <p role="alert">{props.posts.message}</p>;
    case "ready":
      if (props.posts.value.length === 0) {
        return <p>{props.query ? "No posts match your search." : "No posts yet. Create the first one."}</p>;
      }
      return (
        <div>
          {props.posts.value.map((post) => (
            <article key={post.id} style={styles.post}>
              <h3><Link to={BlogRoutes.Post({ postId: post.id })}>{post.title}</Link></h3>
              <p>{post.body.length > 180 ? `${post.body.slice(0, 180)}…` : post.body}</p>
            </article>
          ))}
        </div>
      );
  }
}
