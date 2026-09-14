import { Platform, StatusBar } from 'react-native';
import { platformDefaultInsets, resolveInsets } from '../insets';

const originalOS = Platform.OS;
const originalStatusBarHeight = StatusBar.currentHeight;

afterEach(() => {
  Platform.OS = originalOS;
  StatusBar.currentHeight = originalStatusBarHeight;
});

describe('platformDefaultInsets', () => {
  it('usa um padrão conservador no iOS (notch e indicador de início)', () => {
    Platform.OS = 'ios';
    expect(platformDefaultInsets()).toEqual({ top: 47, right: 0, bottom: 34, left: 0 });
  });

  it('usa a altura da barra de status no Android', () => {
    Platform.OS = 'android';
    StatusBar.currentHeight = 30;
    expect(platformDefaultInsets()).toEqual({ top: 30, right: 0, bottom: 0, left: 0 });
  });

  it('cai num padrão de 24 no Android quando a barra de status não informa altura', () => {
    Platform.OS = 'android';
    StatusBar.currentHeight = undefined;
    expect(platformDefaultInsets()).toEqual({ top: 24, right: 0, bottom: 0, left: 0 });
  });
});

describe('resolveInsets', () => {
  it('usa `insets` fixos quando não há `getInsets`', () => {
    const insets = { top: 1, right: 2, bottom: 3, left: 4 };
    expect(resolveInsets(insets, undefined)).toBe(insets);
  });

  it('prioriza `getInsets` sobre `insets` fixos', () => {
    const fixed = { top: 1, right: 2, bottom: 3, left: 4 };
    const fromApp = { top: 10, right: 20, bottom: 30, left: 40 };
    expect(resolveInsets(fixed, () => fromApp)).toBe(fromApp);
  });

  it('cai no padrão da plataforma quando `getInsets` lança', () => {
    Platform.OS = 'ios';
    const result = resolveInsets(undefined, () => {
      throw new Error('boom');
    });
    expect(result).toEqual({ top: 47, right: 0, bottom: 34, left: 0 });
  });

  it('cai no padrão da plataforma sem `insets` nem `getInsets`', () => {
    Platform.OS = 'ios';
    expect(resolveInsets(undefined, undefined)).toEqual({ top: 47, right: 0, bottom: 34, left: 0 });
  });
});
