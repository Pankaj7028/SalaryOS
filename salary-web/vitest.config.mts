import { defineConfig } from "vitest/config";

// .mts because Vite loads the config natively as ESM; a .ts file here is treated
// as CommonJS and warns. tsconfig `paths` (the @/* alias) resolve natively, so
// the vite-tsconfig-paths plugin is not needed.
export default defineConfig({
  resolve: { tsconfigPaths: true },
  test: {
    environment: "node",
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
  },
});
