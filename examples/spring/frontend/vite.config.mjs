import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";

export default defineConfig({
  base: "/bundles/",
  build: {
    outDir: "../build/generated/frontend/static/bundles",
    emptyOutDir: true,
    cssCodeSplit: false,
    rolldownOptions: {
      input: fileURLToPath(new URL("./src/main.tsx", import.meta.url)),
      output: {
        entryFileNames: "blog.bundle.js",
        chunkFileNames: "[name]-[hash].js",
        assetFileNames: (asset) => (
          asset.names.some((name) => name.endsWith(".css")) ? "blog.css" : "[name]-[hash][extname]"
        ),
      },
    },
  },
});
