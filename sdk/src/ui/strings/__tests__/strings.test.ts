import { DEFAULT_STRINGS, mergeStrings } from '../strings';

describe('textos da UI padrão', () => {
  it('os rótulos padrão vêm em pt-BR', () => {
    expect(DEFAULT_STRINGS.next).toBe('Próxima');
    expect(DEFAULT_STRINGS.back).toBe('Voltar');
    expect(DEFAULT_STRINGS.submit).toBe('Enviar');
    expect(DEFAULT_STRINGS.close).toBe('Fechar');
    expect(DEFAULT_STRINGS.requiredBadge).toBe('obrigatória');
    expect(DEFAULT_STRINGS.progress({ position: 2, total: 5 })).toBe('Pergunta 2 de 5');
  });

  it('mergeStrings substitui só os rótulos informados', () => {
    const merged = mergeStrings({ next: 'Next', progress: ({ position, total }) => `${position}/${total}` });
    expect(merged.next).toBe('Next');
    expect(merged.back).toBe(DEFAULT_STRINGS.back);
    expect(merged.progress({ position: 1, total: 3 })).toBe('1/3');
  });

  it('sem substituto nenhum, mergeStrings devolve o padrão', () => {
    expect(mergeStrings(undefined)).toBe(DEFAULT_STRINGS);
  });
});
