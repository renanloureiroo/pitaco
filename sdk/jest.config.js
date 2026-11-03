/** @type {import('jest').Config} */
module.exports = {
  preset: '@react-native/jest-preset',
  roots: ['<rootDir>/src'],
  testMatch: ['**/__tests__/**/*.test.ts?(x)'],
  moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json'],
  transformIgnorePatterns: ['node_modules/(?!((jest-)?react-native|@react-native(-community)?)/)'],
  setupFilesAfterEnv: ['<rootDir>/jest.setup.ts'],
  modulePathIgnorePatterns: ['<rootDir>/lib/'],
  clearMocks: true,
  // Os testes de apresentação montam a árvore inteira do React Native e esperam animações
  // simuladas: em runner de CI com dois núcleos, 5 s (o padrão) estoura sem defeito nenhum.
  testTimeout: 20000,
};
