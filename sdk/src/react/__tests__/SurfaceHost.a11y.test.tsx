// Acessibilidade transversal (fase 3d): alvo de toque mínimo de 44 pt na alça do bottom sheet
// (o resto dos controles — botões, chips, pontos de escala, fechar — já é coberto por
// `ui/primitives/__tests__/primitives.test.tsx` e pelos testes de cada renderizador). Harness
// próprio (não o de `SurfaceHost.test.tsx`, de outra subtarefa) para não alterar aquele arquivo.

import { render, screen } from '@testing-library/react-native';
import { StyleSheet, type ViewStyle } from 'react-native';
import { deliverableSurvey } from '../../__tests__/support/fixtures';
import { createPreviewController } from '../../preview';
import { PitacoContext, type PitacoContextValue } from '../context';
import { PitacoSurfaceHost } from '../SurfaceHost';

const NOW = Date.UTC(2026, 8, 13, 10, 0, 0);

function makeContext(): PitacoContextValue {
  const controller = createPreviewController({ schema: deliverableSurvey() });
  return {
    runtime: null,
    controller,
    reportRenderError: () => undefined,
    ui: { presentation: 'bottom-sheet' },
  };
}

beforeEach(() => {
  jest.useFakeTimers({ now: NOW });
});

afterEach(() => {
  jest.useRealTimers();
});

describe('acessibilidade da moldura de apresentação', () => {
  it('a alça do bottom sheet tem ao menos 44 pt de altura tocável', async () => {
    await render(
      <PitacoContext.Provider value={makeContext()}>
        <PitacoSurfaceHost />
      </PitacoContext.Provider>,
    );
    const handle = screen.getByTestId('pitaco-bottom-sheet-handle');
    const style = StyleSheet.flatten(handle.props.style as ViewStyle | readonly ViewStyle[]) as ViewStyle;
    expect(style.minHeight ?? 0).toBeGreaterThanOrEqual(44);
    expect(handle.props.accessibilityRole).toBe('adjustable');
    const label = handle.props.accessibilityLabel as unknown;
    expect(typeof label).toBe('string');
    expect((label as string).length).toBeGreaterThan(0);
  });
});
