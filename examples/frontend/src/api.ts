import type { BlogPost, WritePostRequest } from "./blog";

export class ApiError extends Error {
  constructor(readonly status: number) {
    super(status === 404 ? "This post no longer exists." : "The request failed. Please try again.");
  }
}

async function request(path: string, options: RequestInit): Promise<Response> {
  const response = await fetch(path, {
    ...options,
    headers: {
      Accept: "application/json",
      ...(options.body ? { "Content-Type": "application/json" } : {}),
    },
  });
  if (!response.ok) {
    throw new ApiError(response.status);
  }
  return response;
}

export async function listPosts(query: string, signal: AbortSignal): Promise<BlogPost[]> {
  const search = new URLSearchParams({ q: query });
  const response = await request(`/api/posts?${search}`, { signal });
  return response.json();
}

export async function readPost(postId: string, signal: AbortSignal): Promise<BlogPost> {
  const response = await request(`/api/posts/${encodeURIComponent(postId)}`, { signal });
  return response.json();
}

export async function createPost(input: WritePostRequest, signal: AbortSignal): Promise<BlogPost> {
  const response = await request("/api/posts", { method: "POST", body: JSON.stringify(input), signal });
  return response.json();
}

export async function updatePost(postId: string, input: WritePostRequest, signal: AbortSignal): Promise<BlogPost> {
  const response = await request(`/api/posts/${encodeURIComponent(postId)}`, {
    method: "PUT",
    body: JSON.stringify(input),
    signal,
  });
  return response.json();
}

export async function deletePost(postId: string, signal: AbortSignal): Promise<void> {
  await request(`/api/posts/${encodeURIComponent(postId)}`, { method: "DELETE", signal });
}
