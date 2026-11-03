// @ts-check
import js from '@eslint/js';
import { defineConfig } from 'eslint/config';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

// Pacotes nativos que o SDK nunca importa (restrição 1): quem usa passa a instância.
const forbiddenNative = [
  'react-native-reanimated',
  'react-native-gesture-handler',
  '@gorhom/bottom-sheet',
  'react-native-safe-area-context',
  '@react-native-async-storage/async-storage',
  'react-native-mmkv',
];

export default defineConfig(
  { ignores: ['lib/**', 'node_modules/**', 'coverage/**', 'src/api/openapi.ts', 'example/**'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, tseslint.configs.recommendedTypeChecked],
    plugins: { 'react-hooks': reactHooks },
    languageOptions: {
      parserOptions: { projectService: true, tsconfigRootDir: import.meta.dirname },
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      '@typescript-eslint/consistent-type-imports': 'error',
      '@typescript-eslint/no-floating-promises': 'error',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      'no-console': ['error', { allow: ['warn'] }],
      'no-restricted-imports': [
        'error',
        {
          paths: forbiddenNative.map((name) => ({
            name,
            message: 'O SDK não importa módulo nativo: receba a instância do app.',
          })),
        },
      ],
    },
  },
  {
    files: ['**/__tests__/**'],
    rules: {
      '@typescript-eslint/unbound-method': 'off',
      '@typescript-eslint/require-await': 'off',
      '@typescript-eslint/no-non-null-assertion': 'off',
    },
  },
  {
    files: ['**/*.{js,mjs,cjs}'],
    extends: [js.configs.recommended],
    languageOptions: {
      globals: { require: 'readonly', module: 'writable', process: 'readonly', console: 'readonly', fetch: 'readonly', AbortSignal: 'readonly' },
    },
  },
);
