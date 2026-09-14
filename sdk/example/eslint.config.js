// ESLint do exemplo (Expo). Independente do eslint.config.mjs do SDK — o SDK ignora `example/**`
// e vice-versa; cada um roda o próprio `lint` (o `verify` do SDK chama os dois).
const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');

module.exports = defineConfig([
  expoConfig,
  {
    ignores: ['dist/**', '.expo/**', 'src/generated/**'],
  },
]);
