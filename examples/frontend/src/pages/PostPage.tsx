import { useFetcher, useLoaderData } from "react-router";
import PostReader from "../components/PostReader";
import type { loadPost, removePost } from "../routeData";

export default function PostPage() {
  const post = useLoaderData<typeof loadPost>();
  const deletion = useFetcher<typeof removePost>();
  return (
    <PostReader
      post={{ type: "ready", value: post }}
      deleting={deletion.state !== "idle"}
      deleteError={deletion.data?.error ?? null}
      onDelete={() => void deletion.submit(null, { method: "delete" })}
    />
  );
}
