import { Link } from "react-router";
import type { BlogPost, LoadState } from "../blog";
import { BlogRoutes } from "../routes";

type Props = {
  post: LoadState<BlogPost>;
  onDelete: () => void;
  deleting: boolean;
  deleteError: string | null;
};

const styles = {
  body: { whiteSpace: "pre-wrap" as const, overflowWrap: "anywhere" as const },
  actions: { display: "flex", gap: "1rem", alignItems: "center" },
};

export default function PostReader(props: Props) {
  switch (props.post.type) {
    case "loading":
      return <p role="status">Loading post…</p>;
    case "error":
      return <p role="alert">{props.post.message}</p>;
    case "ready": {
      const post = props.post.value;
      return (
        <article>
          <h2>{post.title}</h2>
          <p style={styles.body}>{post.body}</p>
          <div style={styles.actions}>
            <Link to={BlogRoutes.EditPost({ postId: post.id })}>Edit post</Link>
            <button type="button" disabled={props.deleting} onClick={props.onDelete}>
              {props.deleting ? "Deleting…" : "Delete post"}
            </button>
          </div>
          {props.deleteError && <p role="alert">{props.deleteError}</p>}
        </article>
      );
    }
  }
}
