// `<PitacoBlock />`: bloqueia enquanto montado, desbloqueia ao desmontar, e dois montados ao mesmo
// tempo com o mesmo motivo não se anulam (feature 4).

import { act, render } from '@testing-library/react-native';
import { useEffect, type ReactNode } from 'react';
import { FakePitacoServer } from '../../__tests__/support/fakeServer';
import { deliverableSurvey } from '../../__tests__/support/fixtures';
import type { PitacoListenerEvent } from '../../core/runtime/runtime';
import { PitacoBlock } from '../PitacoBlock';
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
  // Guarda o resultado num efeito, nunca durante o render: mutar uma variável de fora no corpo do
  // componente é o efeito colateral que o lint de pureza de render rejeita.
  useEffect(() => {
    probe = { pitaco, survey };
  });
  return null;
}

function Tree({ a, b, events }: { a: boolean; b: boolean; events: PitacoListenerEvent[] }): ReactNode {
  return (
    <PitacoProvider baseUrl="https://pitaco.test/api" apiKey="pk_test" onEvent={(event) => events.push(event)}>
      <Probe />
      {a && <PitacoBlock reason="pagamento" />}
      {b && <PitacoBlock reason="pagamento" />}
    </PitacoProvider>
  );
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

describe('<PitacoBlock />', () => {
  it('bloqueia enquanto montado e desbloqueia ao desmontar', async () => {
    installServer();
    const events: PitacoListenerEvent[] = [];
    const { rerender } = await render(<Tree a b={false} events={events} />);

    await act(() => probe?.pitaco.track('checkout_completed'));
    expect(probe?.survey.available).toBe(false); // retida pelo bloqueio
    expect(events.map((event) => event.type)).toEqual(['placement_blocked', 'placement_survey_held']);

    await act(async () => {
      await rerender(<Tree a={false} b={false} events={events} />);
    });
    expect(probe?.survey.available).toBe(true); // desmontou o único bloqueio: liberada
    expect(events.at(-1)?.type).toBe('placement_available');
  });

  it('dois <PitacoBlock /> com o mesmo motivo não se anulam: só o último a desmontar libera', async () => {
    installServer();
    const events: PitacoListenerEvent[] = [];
    const { rerender } = await render(<Tree a b events={events} />);

    await act(() => probe?.pitaco.track('checkout_completed'));
    expect(probe?.survey.available).toBe(false);
    expect(events.filter((event) => event.type === 'placement_blocked')).toHaveLength(1);

    await act(async () => {
      await rerender(<Tree a={false} b events={events} />);
    });
    expect(probe?.survey.available).toBe(false); // ainda um `<PitacoBlock />` montado

    await act(async () => {
      await rerender(<Tree a={false} b={false} events={events} />);
    });
    expect(probe?.survey.available).toBe(true);
  });
});
