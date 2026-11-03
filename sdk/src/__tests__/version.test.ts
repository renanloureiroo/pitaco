import packageJson from '../../package.json';
import { SDK_VERSION } from '../version';

describe('versão do SDK', () => {
  it('é a mesma do package.json, porque vai no X-Pitaco-Sdk-Version', () => {
    expect(SDK_VERSION).toBe(packageJson.version);
  });

  it('segue semver e cabe nos 40 caracteres do contrato', () => {
    expect(SDK_VERSION).toMatch(/^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/);
    expect(SDK_VERSION.length).toBeLessThanOrEqual(40);
  });

  it('só declara react e react-native como peer dependencies, sem dependência de runtime', () => {
    const manifest = packageJson as { peerDependencies?: Record<string, string>; dependencies?: Record<string, string> };
    expect(Object.keys(manifest.peerDependencies ?? {}).sort()).toEqual(['react', 'react-native']);
    expect(manifest.dependencies ?? {}).toEqual({});
  });
});
