// `<PitacoPreview presentation="bottom-sheet" | "modal" />`: a pré-visualização abre no mesmo
// contêiner do `<PitacoProvider>` (o cenário 4 do exemplo compara as formas com o schema do seed),
// sem transporte e sem exibição aberta; `onFinish` só chega depois da animação de saída.

import { act, fireEvent, render, screen } from '@testing-library/react-native';
import type { InteractionEvent } from '../../catalog/events';
import { deliverableSurvey } from '../../__tests__/support/fixtures';
import type { PitacoSurveyFinishReason } from '../../ui/content/PitacoSurveyContent';
import { PitacoPreview } from '../PitacoPreview';

const NOW = Date.UTC(2026, 8, 13, 12, 0, 0);

let fetchSpy: jest.Mock;

beforeEach(() => {
  jest.useFakeTimers({ now: NOW });
  fetchSpy = jest.fn(() => Promise.reject(new Error('o preview não fala com a rede')));
  Object.defineProperty(globalThis, 'fetch', { configurable: true, writable: true, value: fetchSpy });
});

afterEach(() => {
  jest.useRealTimers();
});

function presentedAs(events: readonly InteractionEvent[]): string | undefined {
  const presented = events.find((event) => event.type === 'survey_presented');
  return presented === undefined ? undefined : (presented.data as { presentation: string }).presentation;
}

function dismissedVia(events: readonly InteractionEvent[]): string | undefined {
  const dismissed = events.find((event) => event.type === 'survey_dismissed');
  return dismissed === undefined ? undefined : (dismissed.data as { via: string }).via;
}

describe('<PitacoPreview /> dentro do contêiner do SDK', () => {
  it('bottom-sheet: abre a folha do SDK e só emite survey_presented depois da entrada, sem rede', async () => {
    const events: InteractionEvent[] = [];
    await render(
      <PitacoPreview schema={deliverableSurvey()} presentation="bottom-sheet" onEvent={(event) => events.push(event)} />,
    );

    expect(screen.getByTestId('pitaco-bottom-sheet')).toBeTruthy();
    expect(events).toHaveLength(0);

    await act(async () => jest.advanceTimersByTimeAsync(400));
    expect(presentedAs(events)).toBe('bottom-sheet');
    expect(screen.getByText(/O quanto você recomendaria/)).toBeTruthy();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('bottom-sheet: tocar no fundo dispensa com via backdrop e chama onFinish só depois da saída', async () => {
    const events: InteractionEvent[] = [];
    const onFinish = jest.fn<void, [PitacoSurveyFinishReason]>();
    await render(
      <PitacoPreview
        schema={deliverableSurvey()}
        presentation="bottom-sheet"
        onEvent={(event) => events.push(event)}
        onFinish={onFinish}
      />,
    );
    await act(async () => jest.advanceTimersByTimeAsync(400));

    await act(async () => fireEvent.press(screen.getByTestId('pitaco-bottom-sheet-backdrop')));
    expect(dismissedVia(events)).toBe('backdrop');
    expect(onFinish).not.toHaveBeenCalled();

    await act(async () => jest.advanceTimersByTimeAsync(400));
    expect(onFinish).toHaveBeenCalledTimes(1);
    expect(onFinish).toHaveBeenCalledWith('dismissed');
    expect(screen.queryByTestId('pitaco-bottom-sheet')).toBeNull();
  });

  it('bottom-sheet: concluir mostra o agradecimento e entrega onFinish("completed") depois da saída', async () => {
    const onFinish = jest.fn<void, [PitacoSurveyFinishReason]>();
    await render(
      <PitacoPreview schema={deliverableSurvey()} presentation="bottom-sheet" thankYouDurationMs={1000} onFinish={onFinish} />,
    );
    await act(async () => jest.advanceTimersByTimeAsync(400));

    await act(async () => fireEvent.press(screen.getByRole('radio', { name: '9' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    await act(async () => fireEvent.press(screen.getByRole('radio', { name: '4 de 5' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Enviar' })));

    expect(screen.getByText('Obrigado!')).toBeTruthy();
    await act(async () => jest.advanceTimersByTimeAsync(1000));
    expect(onFinish).not.toHaveBeenCalled();
    await act(async () => jest.advanceTimersByTimeAsync(400));
    expect(onFinish).toHaveBeenCalledWith('completed');
  });

  it('modal: abre em tela cheia e relata presentation "modal"', async () => {
    const events: InteractionEvent[] = [];
    await render(
      <PitacoPreview schema={deliverableSurvey()} presentation="modal" onEvent={(event) => events.push(event)} />,
    );

    expect(screen.getByTestId('pitaco-fullscreen-safe-area')).toBeTruthy();
    expect(screen.queryByTestId('pitaco-bottom-sheet')).toBeNull();
    await act(async () => jest.advanceTimersByTimeAsync(300));
    expect(presentedAs(events)).toBe('modal');
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});
