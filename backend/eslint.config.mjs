
import tseslint from "typescript-eslint";

export default tseslint.config(
  {
    ignores: [
      "node_modules/**",
      "dist/**",
      ".vercel/**",
      "coverage/**",
    ],
  },
  ...tseslint.configs.recommended,
);
