// Testa a orquestração das apresentações (`PitacoSurfaceHost`): qual apresentação o `ui.presentation`
// do contexto escolhe, que `survey_presented` só sai depois da animação de entrada terminar, que
// cada via de dispensa chega ao core, que `insets` (fixos e `getInsets`) chegam à apresentação, que
// `inline` não abre contêiner nenhum (nem depois, ao trocar de apresentação), e que uma falha aqui
// não derruba a árvore do app — a mesma
// garantia que o Provider (fase 2) já dá ao próprio `PitacoSurfaceHost`.
//
// Construído sobre um `PitacoContextValue` de mentira (não o `PitacoPreviewProvider`): o controlador
// vem de `createPreviewController` (o mesmo do preview, fase 2/3a), mas montado à mão para poder
// injetar `insets`/`getInsets` no `ui` do contexto — o preview ainda não os aceita (fora da posse
// desta subtarefa; ver o relatório).

import { act, fireEvent, render, screen } from '@testing-library/react-native';
import { useEffect } from 'react';
import { AccessibilityInfo, BackHandler, Platform, Text, View } from 'react-native';
import type { InteractionEvent, Presentation } from '../../catalog/events';
import { deliverableSurvey } from '../../__tests__/support/fixtures';
import { createPreviewController } from '../../preview';
import type { EdgeInsets } from '../../ui/types';
import { PitacoContext, type PitacoContextValue } from '../context';
import { PitacoErrorBoundary } from '../ErrorBoundary';
import { PitacoSurfaceHost } from '../SurfaceHost';
import { usePitacoSurvey } from '../usePitacoSurvey';

const NOW = Date.UTC(2026, 8, 13, 10, 0, 0);

function dismissedVia(events: readonly InteractionEvent[]): string | undefined {
  const dismissed = events.find((event) => event.type === 'survey_dismissed');
  return dismissed === undefined ? undefined : (dismissed as { data: { via: string } }).data.via;
}

interface ContextOptions {
  readonly presentation?: Presentation;
  readonly onEvent?: (event: InteractionEvent) => void;
  readonly insets?: EdgeInsets;
  readonly getInsets?: () => EdgeInsets;
}

function makeContext(options: ContextOptions = {}): PitacoContextValue {
  const controller = createPreviewController({
    schema: deliverableSurvey(),
    ...(options.presentation === undefined ? {} : { presentation: options.presentation }),
    ...(options.onEvent === undefined ? {} : { onEvent: options.onEvent }),
  });
  return {
    runtime: null,
    controller,
    reportRenderError: () => undefined,
    ui: {
      presentation: options.presentation ?? 'bottom-sheet',
      ...(options.insets === undefined ? {} : { insets: options.insets }),
      ...(options.getInsets === undefined ? {} : { getInsets: options.getInsets }),
    },
  };
}

function Harness(props: { readonly value: PitacoContextValue }) {
  return (
    <PitacoContext.Provider value={props.value}>
      <PitacoSurfaceHost />
    </PitacoContext.Provider>
  );
}

beforeEach(() => {
  jest.useFakeTimers({ now: NOW });
});

afterEach(() => {
  jest.useRealTimers();
  jest.restoreAllMocks();
});

describe('<PitacoSurfaceHost /> — bottom sheet (padrão)', () => {
  it('abre a folha quando a pesquisa fica disponível, e só emite survey_presented depois da entrada', async () => {
    const events: InteractionEvent[] = [];
    await render(<Harness value={makeContext({ onEvent: (event) => events.push(event) })} />);

    expect(screen.getByTestId('pitaco-bottom-sheet')).toBeTruthy();
    expect(events.some((event) => event.type === 'survey_presented')).toBe(false);

    await act(async () => jest.advanceTimersByTimeAsync(400));
    expect(events.some((event) => event.type === 'survey_presented')).toBe(true);
  });

  it('o botão de fechar do conteúdo dispensa com via close_button', async () => {
    const events: InteractionEvent[] = [];
    await render(<Harness value={makeContext({ onEvent: (event) => events.push(event) })} />);
    await act(async () => jest.advanceTimersByTimeAsync(400));

    const buttons = screen.getAllByRole('button', { name: 'Fechar pesquisa' });
    const closeButton = buttons.find((button) => button.props.testID !== 'pitaco-bottom-sheet-backdrop');
    await act(async () => fireEvent.press(closeButton!));

    expect(dismissedVia(events)).toBe('close_button');
  });

  it('tocar no fundo dispensa com via backdrop, e a folha some depois da saída', async () => {
    const events: InteractionEvent[] = [];
    await render(<Harness value={makeContext({ onEvent: (event) => events.push(event) })} />);
    await act(async () => jest.advanceTimersByTimeAsync(400));

    await act(async () => fireEvent.press(screen.getByTestId('pitaco-bottom-sheet-backdrop')));
    expect(dismissedVia(events)).toBe('backdrop');

    await act(async () => jest.advanceTimersByTimeAsync(400));
    expect(screen.queryByTestId('pitaco-bottom-sheet')).toBeNull();
  });

  it('o voltar de hardware no Android dispensa com via hardware_back', async () => {
    const originalOS = Platform.OS;
    Platform.OS = 'android';
    let handler: (() => boolean) | undefined;
    jest.spyOn(BackHandler, 'addEventListener').mockImplementation((_event, fn) => {
      handler = fn as unknown as () => boolean;
      return { remove: jest.fn() };
    });
    try {
      const events: InteractionEvent[] = [];
      await render(<Harness value={makeContext({ onEvent: (event) => events.push(event) })} />);
      await act(async () => jest.advanceTimersByTimeAsync(400));

      expect(handler).toBeDefined();
      await act(async () => {
        handler?.();
      });
      expect(dismissedVia(events)).toBe('hardware_back');
    } finally {
      Platform.OS = originalOS;
    }
  });

  it('uma dispensa vinda de fora (ex.: usePitacoSurvey().dismiss() num app headless) chega com via programmatic e fecha o contêiner', async () => {
    const events: InteractionEvent[] = [];
    const value = makeContext({ onEvent: (event) => events.push(event) });
    let dismissFromOutside = () => undefined as void;
    function OutsideControl() {
      const survey = usePitacoSurvey();
      useEffect(() => {
        dismissFromOutside = () => survey.dismiss();
      });
      return null;
    }

    await render(
      <PitacoContext.Provider value={value}>
        <PitacoSurfaceHost />
        <OutsideControl />
      </PitacoContext.Provider>,
    );
    await act(async () => jest.advanceTimersByTimeAsync(400));

    await act(async () => dismissFromOutside());
    expect(dismissedVia(events)).toBe('programmatic');

    await act(async () => jest.advanceTimersByTimeAsync(400));
    expect(screen.queryByTestId('pitaco-bottom-sheet')).toBeNull();
  });

  it('com movimento reduzido, abre (e emite survey_presented) sem esperar a animação de verdade', async () => {
    jest.spyOn(AccessibilityInfo, 'isReduceMotionEnabled').mockResolvedValue(true);
    const events: InteractionEvent[] = [];
    await render(<Harness value={makeContext({ onEvent: (event) => events.push(event) })} />);

    // Tempo suficiente só para o `useReduceMotion` assumir o valor do sistema e a animação
    // "instantânea" (1 ms) terminar — bem menos que a duração normal (280 ms).
    await act(async () => jest.advanceTimersByTimeAsync(20));
    expect(events.some((event) => event.type === 'survey_presented')).toBe(true);
  });

  it('aplica os insets fixos no rodapé da folha', async () => {
    const value = makeContext({ insets: { top: 1, right: 2, bottom: 40, left: 4 } });
    await render(<Harness value={value} />);
    expect(screen.getByTestId('pitaco-bottom-sheet')).toHaveStyle({ paddingBottom: 40 });
  });

  it('ancora a folha embaixo da tela (contêiner ocupa a altura toda e alinha no fim)', async () => {
    await render(<Harness value={makeContext()} />);
    expect(screen.getByTestId('pitaco-bottom-sheet-container')).toHaveStyle({ flex: 1, justifyContent: 'flex-end' });
  });
});

describe('<PitacoSurfaceHost /> — modal (tela cheia)', () => {
  it('abre em tela cheia, e só emite survey_presented depois da entrada', async () => {
    const events: InteractionEvent[] = [];
    const value = makeContext({ presentation: 'modal', onEvent: (event) => events.push(event) });
    await render(<Harness value={value} />);

    expect(screen.getByTestId('pitaco-fullscreen-safe-area')).toBeTruthy();
    expect(events.some((event) => event.type === 'survey_presented')).toBe(false);

    await act(async () => jest.advanceTimersByTimeAsync(300));
    expect(events.some((event) => event.type === 'survey_presented')).toBe(true);
  });

  it('usa `getInsets` com prioridade sobre `insets` fixos', async () => {
    const getInsets = jest.fn(() => ({ top: 12, right: 0, bottom: 24, left: 0 }));
    const value = makeContext({
      presentation: 'modal',
      insets: { top: 1, right: 1, bottom: 1, left: 1 },
      getInsets,
    });
    await render(<Harness value={value} />);

    expect(getInsets).toHaveBeenCalled();
    expect(screen.getByTestId('pitaco-fullscreen-safe-area')).toHaveStyle({ paddingTop: 12, paddingBottom: 24 });
  });

  it('o voltar de hardware no Android dispensa com via hardware_back', async () => {
    const originalOS = Platform.OS;
    Platform.OS = 'android';
    let handler: (() => boolean) | undefined;
    jest.spyOn(BackHandler, 'addEventListener').mockImplementation((_event, fn) => {
      handler = fn as unknown as () => boolean;
      return { remove: jest.fn() };
    });
    try {
      const events: InteractionEvent[] = [];
      const value = makeContext({ presentation: 'modal', onEvent: (event) => events.push(event) });
      await render(<Harness value={value} />);
      await act(async () => jest.advanceTimersByTimeAsync(300));

      expect(handler).toBeDefined();
      await act(async () => {
        handler?.();
      });
      expect(dismissedVia(events)).toBe('hardware_back');
    } finally {
      Platform.OS = originalOS;
    }
  });
});

describe('<PitacoSurfaceHost /> — inline', () => {
  it('não renderiza nada: o SDK não abre contêiner nenhum', async () => {
    const value = makeContext({ presentation: 'inline' });
    const { toJSON } = await render(<Harness value={value} />);
    expect(toJSON()).toBeNull();
  });

  // O caso da UI headless do exemplo (cenário 9): a pesquisa chega e termina em `inline`, e depois o
  // app volta à apresentação padrão. Nada em `inline` fecha o contêiner, e ele abria vazio por cima
  // do app (Modal com o fundo escurecido e só a alça), engolindo todos os toques.
  it('uma pesquisa vista em inline não deixa o contêiner aberto quando a apresentação muda', async () => {
    const value = makeContext({ presentation: 'inline' });
    const { rerender } = await render(<Harness value={value} />);
    await act(async () => {
      value.controller?.present('inline');
      value.controller?.dispatch({ type: 'dismiss', via: 'navigation' });
    });

    await rerender(<Harness value={{ ...value, ui: { presentation: 'bottom-sheet' } }} />);

    expect(screen.queryByTestId('pitaco-bottom-sheet')).toBeNull();
  });

  it('uma pesquisa ainda disponível abre no contêiner quando a apresentação sai de inline', async () => {
    const value = makeContext({ presentation: 'inline' });
    const { rerender } = await render(<Harness value={value} />);

    await rerender(<Harness value={{ ...value, ui: { presentation: 'bottom-sheet' } }} />);

    expect(screen.getByTestId('pitaco-bottom-sheet')).toBeTruthy();
  });
});

describe('<PitacoSurfaceHost /> — contenção de falha', () => {
  it('uma falha dentro da apresentação não derruba a árvore do app', async () => {
    const onError = jest.fn();
    const throwingValue: PitacoContextValue = {
      runtime: null,
      controller: {
        getSnapshot: () => {
          throw new Error('falha simulada dentro da apresentação');
        },
        subscribe: () => () => undefined,
        present: () => undefined,
        dispatch: () => undefined,
      },
      reportRenderError: onError,
      ui: { presentation: 'bottom-sheet' },
    };

    await render(
      <View>
        <Text>app continua de pé</Text>
        <PitacoErrorBoundary onError={onError}>
          <Harness value={throwingValue} />
        </PitacoErrorBoundary>
      </View>,
    );

    expect(screen.getByText('app continua de pé')).toBeTruthy();
    expect(onError).toHaveBeenCalled();
  });
});
