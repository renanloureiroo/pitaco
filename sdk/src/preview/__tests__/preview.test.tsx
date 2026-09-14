import { act, renderHook } from '@testing-library/react-native';
import type { ReactNode } from 'react';
import type { InteractionEvent } from '../../catalog/events';
import { deliverableSurvey, KEYS, referenceSurvey } from '../../__tests__/support/fixtures';
import { createMachineDriver, runScript, signature, STANDARD_SCRIPT, toAction } from '../../__tests__/support/script';
import { createPreviewController, PitacoPreviewProvider, usePitacoSurvey } from '..';

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

describe('preview', () => {
  it('roda a mesma máquina sobre o schema em memória, sem rede, entregando os eventos só ao onEvent', async () => {
    const events: InteractionEvent[] = [];
    const draft = deliverableSurvey();
    delete draft.surveyId;
    delete draft.versionId;
    const controller = createPreviewController({ schema: draft, triggerEvent: 'checkout_completed', onEvent: (event) => events.push(event) });
    expect(controller.survey?.questions).toHaveLength(5);

    await runScript(
      {
        perform: (step) => {
          if (step.do === 'present') return controller.present('bottom-sheet');
          if (step.do === 'background' || step.do === 'foreground') return undefined;
          const action = toAction(step);
          if (action.type !== 'present' && action.type !== 'background' && action.type !== 'foreground' && action.type !== 'tick') {
            controller.dispatch(action);
          }
          return undefined;
        },
        wait: async (ms) => {
          await jest.advanceTimersByTimeAsync(ms);
        },
      },
      STANDARD_SCRIPT.filter((step) => step.do !== 'background' && step.do !== 'foreground'),
    );

    const reference = createMachineDriver(referenceSurvey());
    await runScript(
      reference.driver,
      STANDARD_SCRIPT.filter((step) => step.do !== 'background' && step.do !== 'foreground'),
    );
    expect(signature(events)).toEqual(signature(reference.events));
    expect(controller.getSnapshot()?.status).toBe('completed');
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('restart começa outra passada com outro displayId', () => {
    const controller = createPreviewController({ schema: deliverableSurvey() });
    const first = controller.getSnapshot()?.displayId;
    controller.present();
    controller.dispatch({ type: 'dismiss', via: 'close_button' });
    expect(controller.getSnapshot()?.status).toBe('dismissed');
    controller.restart();
    expect(controller.getSnapshot()).toMatchObject({ status: 'ready' });
    expect(controller.getSnapshot()?.displayId).not.toBe(first);
  });

  it('schema sem pergunta renderizável não quebra: não há o que exibir', () => {
    const controller = createPreviewController({ schema: { questions: [{ key: 'x', type: 'MATRIX' }] } });
    expect(controller.survey).toBeNull();
    expect(controller.getSnapshot()).toBeNull();
    expect(() => controller.present()).not.toThrow();
    expect(createPreviewController({ schema: 'lixo' }).survey).toBeNull();
  });

  it('usePitacoSurvey funciona dentro do PitacoPreviewProvider', async () => {
    const events: InteractionEvent[] = [];
    const schema = deliverableSurvey();
    function Wrapper({ children }: { children: ReactNode }) {
      return (
        <PitacoPreviewProvider schema={schema} onEvent={(event) => events.push(event)}>
          {children}
        </PitacoPreviewProvider>
      );
    }
    const { result } = await renderHook(() => usePitacoSurvey(), { wrapper: Wrapper });
    expect(result.current).toMatchObject({ available: true, question: { key: KEYS.nps } });
    await act(() => result.current.present());
    await act(() => result.current.select(10));
    await act(() => result.current.next());
    expect(result.current.question?.key).toBe(KEYS.features);
    expect(events.map((event) => event.type)).toContain('question_not_applicable');
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});
