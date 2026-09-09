import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTypescript from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTypescript,
  {
    rules: {
      // Parâmetro prefixado por `_` é intencionalmente ignorado — comum em dublês de teste que
      // precisam da assinatura completa sem usar todos os argumentos.
      "@typescript-eslint/no-unused-vars": [
        "warn",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_", caughtErrorsIgnorePattern: "^_" },
      ],
      // Princípio I: features só podem ser importadas pela sua fronteira pública.
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["@/features/*/*"],
              message:
                "Importe a feature pelo seu index.ts (@/features/<feature>). Caminhos internos são privados — ver Princípio I da constituição.",
            },
          ],
        },
      ],
    },
  },
  globalIgnores([".next/**", "out/**", "build/**", "next-env.d.ts", "coverage/**", "playwright-report/**", "test-results/**"]),
]);

export default eslintConfig;
