import type { ReactNode } from "react";
import { Link } from "react-router";
import { BlogRoutes } from "../routes";

type Props = {
  children: ReactNode;
};

const styles = {
  page: { maxWidth: "48rem", margin: "2rem auto", padding: "0 1rem", fontFamily: "system-ui, sans-serif", lineHeight: 1.6 },
  navigation: { display: "flex", gap: "1rem" },
};

export default function BlogLayout(props: Props) {
  return (
    <div style={styles.page}>
      <header>
        <h1>Blog</h1>
        <nav aria-label="Main" style={styles.navigation}>
          <Link to={BlogRoutes.Index()}>All posts</Link>
          <Link to={BlogRoutes.NewPost()}>New post</Link>
        </nav>
      </header>
      <main>{props.children}</main>
    </div>
  );
}
