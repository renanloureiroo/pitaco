// Troca de runtime no `<PitacoProvider>`: quando a configuração muda, o runtime substituído é
// encerrado de vez (temporizadores, ouvinte do `AppState`, fila), sem perder o que já estava gravado
// na fila persistente.

import { act, render } from '@testing-library/react-native';
import { useEffect, type ReactNode } from 'react';
import { AppState } from 'react-native';
import { FakePitacoServer } from '../../__tests__/support/fakeServer';
import { deliverableSurvey } from '../../__tests__/support/fixtures';
import type { PitacoListenerEvent } from '../../core/runtime/runtime';
import { createMemoryStorage } from '../../core/storage/memory';
import type { PitacoStorage } from '../../core/storage/types';
import { PitacoProvider } from '../PitacoProvider';
import { usePitaco, type PitacoActions } from '../usePitaco';
import { usePitacoSurvey, type UsePitacoSurveyResult } from '../usePitacoSurvey';

const NOW = Date.UTC(2026, 8, 12, 13, 0, 0);

function installServer() {
  const server = new FakePitacoServer();
  server.survey = deliverableSurvey();
  Object.defineProperty(globalThis, 'fetch', { configurable: true, writable: true, value: server.fetch });
  return server;
}

let probe: { pitaco: PitacoActions; survey: UsePitacoSurveyResult } | null = null;

function Probe(): null {
  const pitaco = usePitaco();
  const survey = usePitacoSurvey();
  useEffect(() => {
    probe = { pitaco, survey };
  });
  return null;
}

interface TreeProps {
  readonly events: PitacoListenerEvent[];
  readonly storage: PitacoStorage;
  readonly deferTimeoutMs?: number;
  readonly eligibilityTimeoutMs?: number;
}

function Tree({ events, storage, deferTimeoutMs, eligibilityTimeoutMs }: TreeProps): ReactNode {
  return (
    <PitacoProvider
      baseUrl="https://pitaco.test/api"
      apiKey="pk_test"
      presentation="inline"
      storage={storage}
      deferTimeoutMs={deferTimeoutMs}
      eligibilityTimeoutMs={eligibilityTimeoutMs}
      onEvent={(event) => events.push(event)}
    >
      <Probe />
    </PitacoProvider>
  );
}

function lastAppStateSubscription(): { remove: jest.Mock } {
  const results = jest.mocked(AppState.addEventListener).mock.results;
  return results.at(-1)?.value as { remove: jest.Mock };
}

let warn: jest.SpyInstance;

beforeEach(() => {
  jest.useFakeTimers({ now: NOW });
  probe = null;
  warn = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
});

afterEach(() => {
  warn.mockRestore();
  jest.useRealTimers();
});

describe('<PitacoProvider> trocando de runtime', () => {
  it('encerra o runtime substituído: o prazo antigo não emite placement_expired e o AppState antigo é solto', async () => {
    installServer();
    const events: PitacoListenerEvent[] = [];
    const storage = createMemoryStorage();
    const { rerender } = await render(<Tree events={events} storage={storage} deferTimeoutMs={10_000} />);
    const oldSubscription = lastAppStateSubscription();

    await act(async () => {
      probe?.pitaco.defer();
      await probe?.pitaco.track('checkout_completed');
    });
    expect(events.map((event) => event.type)).toEqual(['placement_deferred']);
    expect(probe?.pitaco.diagnostics()?.held).toBe(true);

    await act(async () => {
      await rerender(<Tree events={events} storage={storage} deferTimeoutMs={60_000} />);
    });
    expect(oldSubscription.remove).toHaveBeenCalled();
    expect(lastAppStateSubscription()).not.toBe(oldSubscription);
    expect(probe?.pitaco.diagnostics()?.held).toBe(false);

    await act(async () => {
      await jest.advanceTimersByTimeAsync(15_000);
    });
    expect(events.map((event) => event.type)).toEqual(['placement_deferred']);
  });

  it('o que já estava gravado na fila persistente sobrevive à troca e sai uma vez pelo runtime novo', async () => {
    const server = installServer();
    const events: PitacoListenerEvent[] = [];
    const storage = createMemoryStorage();
    const { rerender } = await render(<Tree events={events} storage={storage} />);

    await act(() => probe?.pitaco.track('checkout_completed'));
    expect(probe?.survey.available).toBe(true);

    server.behavior = 'down';
    await act(async () => {
      probe?.survey.present('inline');
      probe?.survey.select(9);
      probe?.survey.dismiss('close_button');
      await jest.advanceTimersByTimeAsync(100);
    });
    expect(server.submissions.size).toBe(0);
    expect(probe?.pitaco.diagnostics()?.queue.some((item) => item.kind === 'submission')).toBe(true);

    server.behavior = 'up';
    await act(async () => {
      await rerender(<Tree events={events} storage={storage} eligibilityTimeoutMs={4_000} />);
      await jest.advanceTimersByTimeAsync(100);
    });
    expect(server.submissions.size).toBe(1);
    expect(server.count('submission')).toBe(1); // sem rede, a fila parou já na abertura
    expect(probe?.pitaco.diagnostics()?.queue).toEqual([]);
  });
});
