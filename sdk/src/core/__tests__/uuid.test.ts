import { createUuidGenerator, formatUuidV4, isUuid, isUuidV4 } from '../uuid';

describe('UUID v4', () => {
  it('usa crypto.randomUUID quando existe', () => {
    const generate = createUuidGenerator({
      getCrypto: () => ({ randomUUID: () => '3B1F0A2C-6C9A-4A1E-9D0B-2C1F7A3E5D90' }),
    });
    expect(generate()).toBe('3b1f0a2c-6c9a-4a1e-9d0b-2c1f7a3e5d90');
  });

  it('cai para getRandomValues quando randomUUID devolve algo que não é v4', () => {
    const getRandomValues = jest.fn((bytes: Uint8Array) => bytes.fill(0xab));
    const generate = createUuidGenerator({
      getCrypto: () => ({ randomUUID: () => 'não-é-uuid', getRandomValues }),
    });
    expect(generate()).toBe('abababab-abab-4bab-abab-abababababab');
    expect(getRandomValues).toHaveBeenCalledTimes(1);
  });

  it('usa o gerador próprio quando não há crypto, como no React Native sem polyfill', () => {
    const generate = createUuidGenerator({ getCrypto: () => undefined });
    for (let index = 0; index < 2000; index += 1) {
      const value = generate();
      expect(isUuidV4(value)).toBe(true);
      expect(value[14]).toBe('4');
      expect('89ab').toContain(value[19]);
    }
  });

  it('o gerador próprio não repete em dez mil chamadas', () => {
    const generate = createUuidGenerator({ getCrypto: () => undefined });
    const values = new Set(Array.from({ length: 10_000 }, () => generate()));
    expect(values.size).toBe(10_000);
  });

  it('com a mesma fonte aleatória produz o mesmo valor', () => {
    const sequence = (seed: number) => {
      let state = seed;
      return () => {
        state = (state * 1103515245 + 12345) % 2 ** 31;
        return state / 2 ** 31;
      };
    };
    const first = createUuidGenerator({ getCrypto: () => undefined, random: sequence(7) });
    const second = createUuidGenerator({ getCrypto: () => undefined, random: sequence(7) });
    expect(first()).toBe(second());
  });

  it('crypto que lança não escapa: o gerador próprio assume', () => {
    const generate = createUuidGenerator({
      getCrypto: () => ({
        randomUUID: () => {
          throw new Error('indisponível');
        },
        getRandomValues: () => {
          throw new Error('indisponível');
        },
      }),
    });
    expect(isUuidV4(generate())).toBe(true);
  });

  it('formata bytes conhecidos com versão 4 e variante RFC', () => {
    expect(formatUuidV4(new Uint8Array(16).fill(0xff))).toBe('ffffffff-ffff-4fff-bfff-ffffffffffff');
    expect(formatUuidV4(new Uint8Array(16))).toBe('00000000-0000-4000-8000-000000000000');
    expect(() => formatUuidV4(new Uint8Array(15))).toThrow(RangeError);
  });

  it('reconhece UUID em qualquer versão e v4 em particular', () => {
    expect(isUuid('0d0f8a5e-1f1b-1c2b-9a2f-3f0f2b7d5c11')).toBe(true);
    expect(isUuidV4('0d0f8a5e-1f1b-1c2b-9a2f-3f0f2b7d5c11')).toBe(false);
    expect(isUuid('0d0f8a5e1f1b4c2b9a2f3f0f2b7d5c11')).toBe(false);
    expect(isUuid(42)).toBe(false);
  });
});
