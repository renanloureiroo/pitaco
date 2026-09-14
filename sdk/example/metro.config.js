// Metro do exemplo: consome o código-fonte do SDK (`sdk/src`), não o pacote publicado. Editar o
// SDK recarrega o exemplo na hora.
//
// - `watchFolders` inclui a raiz do SDK, para o Metro enxergar `sdk/src`.
// - `@pitaco/react-native` e os subpaths públicos apontam direto para `sdk/src` (sem `lib/`).
// - Uma única cópia de `react`/`react-native`: `sdk/node_modules` (instalado só para o lint, o
//   typecheck e os testes do SDK) fica bloqueado, e todo import sem caminho feito de dentro de
//   `sdk/src` é resolvido como se viesse do exemplo.
const { getDefaultConfig } = require('expo/metro-config');
const path = require('node:path');

const projectRoot = __dirname;
const sdkRoot = path.resolve(projectRoot, '..');
const sdkSrc = path.join(sdkRoot, 'src');

const SDK_ENTRIES = {
  '@pitaco/react-native': path.join(sdkSrc, 'index.ts'),
  '@pitaco/react-native/preview': path.join(sdkSrc, 'preview', 'index.ts'),
  '@pitaco/react-native/storage/async-storage': path.join(sdkSrc, 'storage', 'async-storage.ts'),
  '@pitaco/react-native/storage/mmkv': path.join(sdkSrc, 'storage', 'mmkv.ts'),
};

const config = getDefaultConfig(projectRoot);

config.watchFolders = [...(config.watchFolders ?? []), sdkRoot];

const escape = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const blocked = [path.join(sdkRoot, 'node_modules'), path.join(sdkRoot, 'lib')].map(
  (dir) => new RegExp(`^${escape(dir)}[\\\\/].*$`),
);
const defaultBlockList = config.resolver.blockList;
config.resolver.blockList = [
  ...(Array.isArray(defaultBlockList) ? defaultBlockList : defaultBlockList ? [defaultBlockList] : []),
  ...blocked,
];

const isBareSpecifier = (name) => !name.startsWith('.') && !path.isAbsolute(name);
const exampleOrigin = path.join(projectRoot, 'package.json');

config.resolver.resolveRequest = (context, moduleName, platform) => {
  const sdkEntry = SDK_ENTRIES[moduleName];
  if (sdkEntry !== undefined) {
    return { type: 'sourceFile', filePath: sdkEntry };
  }
  if (moduleName.startsWith('@pitaco/react-native/')) {
    throw new Error(
      `"${moduleName}" não é um subpath público do SDK. Use um de: ${Object.keys(SDK_ENTRIES).join(', ')}.`,
    );
  }
  if (isBareSpecifier(moduleName) && context.originModulePath.startsWith(sdkSrc + path.sep)) {
    return context.resolveRequest({ ...context, originModulePath: exampleOrigin }, moduleName, platform);
  }
  return context.resolveRequest(context, moduleName, platform);
};

module.exports = config;
