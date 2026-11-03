// Nenhum teste fala com rede de verdade: quem precisa de fetch instala o servidor falso.
Object.defineProperty(globalThis, 'fetch', {
  configurable: true,
  writable: true,
  value: (input: unknown) => Promise.reject(new Error(`fetch não esperado no teste: ${String(input)}`)),
});

// O preset do React Native define `performance.now` com o `Date.now` capturado antes dos timers
// falsos. Resolvido na hora da chamada, o relógio monotônico acompanha `jest.advanceTimersByTime`.
Object.defineProperty(globalThis, 'performance', {
  configurable: true,
  writable: true,
  value: { now: () => Date.now() },
});
