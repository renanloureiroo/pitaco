import { act, render, renderHook, screen } from '@testing-library/react-native';
import type { ReactNode } from 'react';
import { AppState, Text } from 'react-native';
import type { InteractionEvent } from '../../catalog/events';
import type { PitacoListenerEvent } from '../../core/runtime/runtime';
import { FakePitacoServer } from '../../__tests__/support/fakeServer';
import { deliverableSurvey, KEYS, referenceSurvey } from '../../__tests__/support/fixtures';
import {
  createMachineDriver,
  DISMISS_SCRIPT,
  runScript,
  type ScriptDriver,
  signature,
  STANDARD_SCRIPT,
} from '../../__tests__/support/script';
import { PitacoErrorBoundary } from '../ErrorBoundary';
import { PitacoProvider } from '../PitacoProvider';
import { usePitaco } from '../usePitaco';
import { usePitacoSurvey } from '../usePitacoSurvey';

const NOW = Date.UTC(2026, 8, 12, 13, 0, 0);

function installServer() {
  const server = new FakePitacoServer();
  server.survey = deliverableSurvey();
  Object.defineProperty(globalThis, 'fetch', { configurable: true, writable: true, value: server.fetch });
  return server;
}

function wrapperWith(events: PitacoListenerEvent[], extra: Record<string, unknown> = {}) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <PitacoProvider baseUrl="https://pitaco.test/api" apiKey="pk_test" onEvent={(event) => events.push(event)} {...extra}>
        {children}
      </PitacoProvider>
    );
  };
}

function useBoth() {
  return { survey: usePitacoSurvey(), pitaco: usePitaco() };
}

type Hook = { current: ReturnType<typeof useBoth> };

// Driver do roteiro sobre o hook headless: o que uma UI própria faria.
function hookDriver(result: Hook): ScriptDriver {
  const appStateListener = () => {
    const calls = jest.mocked(AppState.addEventListener).mock.calls;
    const listener = calls.at(-1)?.[1] as ((state: string) => void) | undefined;
    if (!listener) throw new Error('o Provider não ouviu o AppState');
    return listener;
  };
  return {
    perform: async (step) => {
      await act(() => {
        const survey = result.current.survey;
        switch (step.do) {
          case 'present':
            return survey.present('bottom-sheet');
          case 'select':
            return survey.select(step.value);
          case 'deselect':
            return survey.deselect(step.value);
          case 'focus':
            return survey.focusText();
          case 'type':
            return survey.setText(step.text);
          case 'blur':
            return survey.blurText();
          case 'next':
            return survey.next();
          case 'back':
            return survey.back();
          case 'complete':
            return survey.complete();
          case 'dismiss':
            return survey.dismiss(step.via);
          case 'background':
            return appStateListener()('background');
          case 'foreground':
            return appStateListener()('active');
        }
      });
    },
    wait: async (ms) => {
      await act(async () => {
        await jest.advanceTimersByTimeAsync(ms);
      });
    },
  };
}

let warn: jest.SpyInstance;

beforeEach(() => {
  jest.useFakeTimers({ now: NOW });
  warn = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
});

afterEach(() => {
  warn.mockRestore();
  jest.useRealTimers();
});

describe('usePitacoSurvey: roteiro de interação', () => {
  it.each([
    ['completo', STANDARD_SCRIPT],
    ['de dispensa', DISMISS_SCRIPT],
  ])('o roteiro %s pelo hook produz a mesma sequência de eventos da máquina', async (_name, script) => {
    const server = installServer();
    const events: PitacoListenerEvent[] = [];
    const { result } = await renderHook(useBoth, { wrapper: wrapperWith(events) });
    await act(() => result.current.pitaco.track('checkout_completed'));
    expect(result.current.survey.available).toBe(true);

    await runScript(hookDriver(result), script);

    const reference = createMachineDriver(referenceSurvey());
    await runScript(reference.driver, script);

    const received = events.filter((event): event is InteractionEvent => 'catalogVersion' in event);
    expect(signature(received)).toEqual(signature(reference.events));

    await act(async () => {
      await jest.advanceTimersByTimeAsync(0);
    });
    expect(server.submissions.size).toBe(1);
  });
});

describe('usePitacoSurvey: estado exposto', () => {
  it('available, pergunta atual, progresso, erros, canGoBack e canGoNext', async () => {
    installServer();
    const events: PitacoListenerEvent[] = [];
    const { result } = await renderHook(useBoth, { wrapper: wrapperWith(events) });
    expect(result.current.survey).toMatchObject({ available: false, status: 'idle', question: null });

    await act(() => result.current.pitaco.track('checkout_completed'));
    expect(result.current.survey).toMatchObject({
      available: true,
      status: 'ready',
      question: { key: KEYS.nps },
      progress: { position: 1, total: 5 },
      canGoBack: false,
      canGoNext: false,
      survey: { questionCount: 5, renderableCount: 5, freeTextNotice: { enabled: true } },
    });

    await act(() => result.current.survey.present());
    await act(() => result.current.survey.next());
    expect(result.current.survey.error).toEqual({ questionKey: KEYS.nps, reason: 'required_missing' });

    await act(() => result.current.survey.select(9));
    expect(result.current.survey).toMatchObject({ value: 9, error: null, canGoNext: true, progress: { total: 4 } });

    await act(() => result.current.survey.next());
    expect(result.current.survey).toMatchObject({ question: { key: KEYS.features }, canGoBack: true, progress: { position: 2 } });

    await act(() => result.current.survey.dismiss('swipe'));
    expect(result.current.survey).toMatchObject({ available: false, status: 'dismissed', question: null });
    expect(events.at(-1)).toMatchObject({ type: 'survey_dismissed', data: { via: 'swipe' } });
  });

  it('ações com questionKey de outra pergunta são ignoradas', async () => {
    installServer();
    const events: PitacoListenerEvent[] = [];
    const { result } = await renderHook(useBoth, { wrapper: wrapperWith(events) });
    await act(() => result.current.pitaco.track('checkout_completed'));
    await act(() => result.current.survey.present());
    await act(() => result.current.survey.select(9, KEYS.rating));
    expect(result.current.survey.value).toBeUndefined();
  });
});

describe('degradação silenciosa na árvore', () => {
  it('fora do Provider os hooks são inertes e avisam em desenvolvimento', async () => {
    const { result } = await renderHook(useBoth);
    expect(result.current.survey.available).toBe(false);
    expect(() => {
      result.current.survey.next();
      result.current.survey.dismiss('swipe');
      result.current.pitaco.setAttributes({ plano: 'pro' });
      result.current.pitaco.block('pagamento');
      result.current.pitaco.unblock('pagamento');
      result.current.pitaco.defer();
      result.current.pitaco.release();
    }).not.toThrow();
    await expect(result.current.pitaco.track('x')).resolves.toBeUndefined();
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('fora do <PitacoProvider>'));
  });

  it('configuração inválida não derruba a árvore do app', async () => {
    await render(
      <PitacoProvider baseUrl="" apiKey="">
        <Text>app segue</Text>
      </PitacoProvider>,
    );
    expect(screen.getByText('app segue')).toBeTruthy();
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('baseUrl é obrigatório'));
  });

  it('erro de renderização do SDK fica isolado e é reportado', async () => {
    const error = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    const onError = jest.fn();
    function Broken(): ReactNode {
      throw new Error('renderizador quebrado');
    }
    await render(
      <>
        <Text>app segue</Text>
        <PitacoErrorBoundary onError={onError}>
          <Broken />
        </PitacoErrorBoundary>
      </>,
    );
    expect(screen.getByText('app segue')).toBeTruthy();
    expect(onError).toHaveBeenCalledWith(expect.any(Error));
    error.mockRestore();
  });

  it('o Provider aceita as props de aparência (tema, textos, renderizadores, slots)', async () => {
    installServer();
    await render(
      <PitacoProvider
        baseUrl="https://pitaco.test/api"
        apiKey="pk_test"
        presentation="inline"
        theme={{ light: { colors: {} } }}
        strings={{ next: 'Next' }}
        renderers={{}}
        slots={{}}
      >
        <Text>ok</Text>
      </PitacoProvider>,
    );
    expect(screen.getByText('ok')).toBeTruthy();
  });
});

describe('usePitaco: diagnóstico (painel de depuração do exemplo)', () => {
  it('fora do Provider diagnostics() é nulo e simulateAppReopen() não lança', async () => {
    const { result } = await renderHook(useBoth);
    expect(result.current.pitaco.diagnostics()).toBeNull();
    expect(() => result.current.pitaco.simulateAppReopen()).not.toThrow();
  });

  it('reporta o estado do runtime e zera o limite de sessão', async () => {
    installServer();
    const { result } = await renderHook(useBoth, { wrapper: wrapperWith([]) });
    await act(() => Promise.resolve());

    let diagnostics = result.current.pitaco.diagnostics();
    expect(diagnostics).not.toBeNull();
    expect(diagnostics?.sessionSurveyShown).toBe(false);
    expect(diagnostics?.deviceId).toEqual(expect.any(String));

    await act(() => result.current.pitaco.track('checkout_completed'));
    await act(() => result.current.survey.present());
    diagnostics = result.current.pitaco.diagnostics();
    expect(diagnostics?.sessionSurveyShown).toBe(true);

    await act(() => result.current.pitaco.simulateAppReopen());
    diagnostics = result.current.pitaco.diagnostics();
    expect(diagnostics?.sessionSurveyShown).toBe(false);
  });
});
