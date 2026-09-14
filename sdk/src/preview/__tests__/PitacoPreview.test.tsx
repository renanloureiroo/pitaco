import { act, fireEvent, render, screen } from '@testing-library/react-native';
import { Text } from 'react-native';
import type { InteractionEvent } from '../../catalog/events';
import { deliverableSurvey } from '../../__tests__/support/fixtures';
import type { PitacoSurveyFinishReason } from '../../ui/content/PitacoSurveyContent';
import { PitacoPreview } from '../PitacoPreview';

const NOW = Date.UTC(2026, 8, 12, 13, 0, 0);

let fetchSpy: jest.Mock;

beforeEach(() => {
  jest.useFakeTimers({ now: NOW });
  fetchSpy = jest.fn(() => Promise.reject(new Error('o preview não fala com a rede')));
  Object.defineProperty(globalThis, 'fetch', { configurable: true, writable: true, value: fetchSpy });
});

afterEach(() => {
  jest.useRealTimers();
});

describe('<PitacoPreview />', () => {
  it('desenha a UI padrão inline (sem Modal), presente sozinha, sem transporte', async () => {
    const events: InteractionEvent[] = [];
    await render(<PitacoPreview schema={deliverableSurvey()} onEvent={(event) => events.push(event)} />);

    expect(screen.getByText(/O quanto você recomendaria/)).toBeTruthy();
    expect(events.some((event) => event.type === 'survey_presented')).toBe(true);
    expect(events[0]?.type === 'survey_presented' && (events[0].data as { presentation: string }).presentation).toBe(
      'inline',
    );
    // Sem contêiner: nenhuma alça de bottom sheet, nenhuma área segura de tela cheia — o
    // `<PitacoPreview />` nunca monta `PitacoSurfaceHost`, sempre inline.
    expect(screen.queryByTestId('pitaco-bottom-sheet')).toBeNull();
    expect(screen.queryByTestId('pitaco-fullscreen-safe-area')).toBeNull();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('percorre a pesquisa e conclui, chamando onFinish', async () => {
    const onFinish = jest.fn<void, [PitacoSurveyFinishReason]>();
    await render(<PitacoPreview schema={deliverableSurvey()} onFinish={onFinish} />);

    await act(async () => fireEvent.press(screen.getByRole('radio', { name: '9' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    await act(async () => fireEvent.press(screen.getByRole('radio', { name: '4 de 5' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Enviar' })));

    expect(screen.getByText('Obrigado!')).toBeTruthy();
    await act(async () => jest.advanceTimersByTimeAsync(2500));
    expect(onFinish).toHaveBeenCalledWith('completed');
  });

  it('aceita tema, textos, renderers e slots substituídos', async () => {
    function CustomNext() {
      return <Text>Ir</Text>;
    }
    await render(
      <PitacoPreview
        schema={deliverableSurvey()}
        theme={{ light: { colors: { primary: '#123456' } } }}
        strings={{ next: 'Avançar' }}
        slots={{ Footer: () => <CustomNext /> }}
      />,
    );
    expect(screen.getByText('Ir')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Avançar' })).toBeNull(); // slot substituído ignora o rótulo padrão
  });

  it('resetKey reinicia a pré-visualização do zero (displayId novo)', async () => {
    const events: InteractionEvent[] = [];
    const schema = deliverableSurvey();
    const { rerender } = await render(
      <PitacoPreview schema={schema} resetKey="a" onEvent={(event) => events.push(event)} />,
    );
    await act(async () => fireEvent.press(screen.getByRole('radio', { name: '9' })));

    const firstPresented = events.find((event) => event.type === 'survey_presented');
    events.length = 0;

    await rerender(<PitacoPreview schema={schema} resetKey="b" onEvent={(event) => events.push(event)} />);

    const secondPresented = events.find((event) => event.type === 'survey_presented');
    expect(secondPresented).toBeDefined();
    expect(secondPresented?.displayId).not.toBe(firstPresented?.displayId);
    // A pesquisa reiniciou do começo: a primeira pergunta (NPS) está sem resposta de novo.
    expect(screen.getByRole('radio', { name: '9' })).toBeTruthy();
  });

  it('schema sem pergunta renderizável não quebra: não desenha nada', async () => {
    await render(<PitacoPreview schema={{ questions: [{ key: 'x', type: 'MATRIX' }] }} />);
    expect(screen.queryByRole('button')).toBeNull();
  });
});
