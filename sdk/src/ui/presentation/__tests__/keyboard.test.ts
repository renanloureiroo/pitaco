import { Platform } from 'react-native';
import { keyboardAvoidingBehavior } from '../keyboard';

describe('keyboardAvoidingBehavior', () => {
  it('usa padding no iOS', () => {
    expect(keyboardAvoidingBehavior('ios')).toBe('padding');
  });

  // Sem `behavior` no Android, a folha dentro do `Modal` ficava atrás do teclado.
  it('usa height no Android, nunca deixa sem behavior', () => {
    expect(keyboardAvoidingBehavior('android')).toBe('height');
  });

  it('lê a plataforma atual quando não recebe nenhuma', () => {
    const originalOS = Platform.OS;
    try {
      Platform.OS = 'android';
      expect(keyboardAvoidingBehavior()).toBe('height');
      Platform.OS = 'ios';
      expect(keyboardAvoidingBehavior()).toBe('padding');
    } finally {
      Platform.OS = originalOS;
    }
  });
});
