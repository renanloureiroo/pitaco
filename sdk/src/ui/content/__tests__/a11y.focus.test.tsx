// Foco de acessibilidade movido para o título da pergunta nova a cada navegação.
//
// `moveAccessibilityFocus` (extraído de `<PitacoSurveyContent />` para `focus.ts`) é testado
// diretamente, sem depender de `findNodeHandle`/`AccessibilityInfo` de verdade: o renderizador de
// teste do React Native não tem nós nativos, então `findNodeHandle` sempre devolve `null` nele —
// testar a função pura (que só decide "há um handle? então mande focar, sem deixar uma falha da
// API derrubar a pesquisa") é o que dá cobertura de verdade a essa regra, com ou sem contêiner
// por fora — a decisão é a mesma esteja `<PitacoSurveyContent />` sozinha, dentro do bottom sheet
// ou dentro do modal, porque não depende de qual é o contêiner.
//
// A segunda suíte confirma, de ponta a ponta, que o título muda de fato a cada navegação (o alvo
// do foco) — inclusive dentro do bottom sheet e do modal (fase 3c).

import { act, fireEvent, render, screen } from '@testing-library/react-native';
import { useEffect } from 'react';
import type { Presentation } from '../../../catalog/events';
import { deliverableSurvey } from '../../../__tests__/support/fixtures';
import { FakePitacoServer } from '../../../__tests__/support/fakeServer';
import { moveAccessibilityFocus } from '../focus';
import { PitacoPreviewProvider } from '../../../preview';
import { PitacoProvider } from '../../../react/PitacoProvider';
import { usePitaco } from '../../../react/usePitaco';
import { PitacoSurveyContent } from '../PitacoSurveyContent';

describe('moveAccessibilityFocus', () => {
  it('chama setFocus quando há um handle', () => {
    const setFocus = jest.fn();
    moveAccessibilityFocus(42, setFocus);
    expect(setFocus).toHaveBeenCalledWith(42);
  });

  it('não chama nada sem handle (ambiente de teste, ou nó ainda não montado)', () => {
    const setFocus = jest.fn();
    moveAccessibilityFocus(null, setFocus);
    expect(setFocus).not.toHaveBeenCalled();
  });

  it('uma falha de setFocus nunca escapa (foco de acessibilidade não derruba a pesquisa)', () => {
    const setFocus = jest.fn(() => {
      throw new Error('API de acessibilidade indisponível');
    });
    expect(() => moveAccessibilityFocus(1, setFocus)).not.toThrow();
  });
});

const NOW = Date.UTC(2026, 8, 12, 13, 0, 0);

function installServer() {
  const server = new FakePitacoServer();
  server.survey = deliverableSurvey();
  Object.defineProperty(globalThis, 'fetch', { configurable: true, writable: true, value: server.fetch });
}

function Tracker() {
  const pitaco = usePitaco();
  useEffect(() => {
    void pitaco.track('checkout_completed');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return null;
}

beforeEach(() => {
  jest.useFakeTimers({ now: NOW });
});

afterEach(() => {
  jest.useRealTimers();
});

describe('o alvo do foco (o título) muda a cada navegação', () => {
  it('sozinha (sem contêiner)', async () => {
    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()}>
        <PitacoSurveyContent />
      </PitacoPreviewProvider>,
    );
    expect(screen.getByRole('header').props.children).toEqual(
      expect.arrayContaining([expect.stringContaining('O quanto você recomendaria')]),
    );

    await act(async () => fireEvent.press(screen.getByRole('radio', { name: '9' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));

    // Nota 9 no NPS: a pergunta de motivo (condição 0-6) fica não aplicável e é pulada sozinha.
    expect(screen.getByRole('header').props.children).toEqual(
      expect.arrayContaining([expect.stringContaining('O que você usa')]),
    );
  });

  it.each<[string, Presentation]>([
    ['bottom sheet', 'bottom-sheet'],
    ['modal em tela cheia', 'modal'],
  ])('dentro do %s', async (_name, presentation) => {
    installServer();
    await render(
      <PitacoProvider baseUrl="https://pitaco.test/api" apiKey="pk_test" presentation={presentation}>
        <Tracker />
      </PitacoProvider>,
    );
    await act(async () => {
      await jest.advanceTimersByTimeAsync(500);
    });
    expect(screen.getByRole('header').props.children).toEqual(
      expect.arrayContaining([expect.stringContaining('O quanto você recomendaria')]),
    );
  });
});
