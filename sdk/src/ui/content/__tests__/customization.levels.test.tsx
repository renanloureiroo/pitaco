// Cobre os níveis de customização ainda sem teste de ponta a ponta sobre `<PitacoSurveyContent />`:
// tema (claro, escuro, inválido degradando), textos substituídos, cada slot substituído,
// renderizador por tipo substituído, renderizador/slot que lança erro (cai no padrão e emite o
// relatório) e headless total. Não duplica os testes unitários já existentes de
// `ui/theme/__tests__/theme.test.ts` (merge/contraste) e `ui/registry/__tests__/SafeCustom.test.tsx`
// (isolamento em si) — aqui o alvo é o comportamento visível através do componente de verdade.

import { act, fireEvent, render, screen } from '@testing-library/react-native';
import type { ReactNode } from 'react';
import { StyleSheet, Text, type TextStyle } from 'react-native';
import { deliverableSurvey } from '../../../__tests__/support/fixtures';
import { PitacoPreviewProvider } from '../../../preview';
import { usePitacoSurvey } from '../../../react/usePitacoSurvey';
import { usePitacoStrings } from '../../strings/useStrings';
import { usePitacoTheme } from '../../theme/useTheme';
import { DEFAULT_DARK_THEME, DEFAULT_LIGHT_THEME } from '../../theme/tokens';
import type { CloseButtonSlotProps, FooterSlotProps, HeaderSlotProps, ProgressSlotProps, QuestionRendererProps, ThankYouSlotProps } from '../../types';
import { PitacoSurveyContent } from '../PitacoSurveyContent';

const NOW = Date.UTC(2026, 8, 12, 13, 0, 0);

let warn: jest.SpyInstance;

beforeEach(() => {
  jest.useFakeTimers({ now: NOW });
  warn = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
});

afterEach(() => {
  warn.mockRestore();
  jest.useRealTimers();
});

function titleColor(): string | undefined {
  const title = screen.getByRole('header');
  const style = StyleSheet.flatten(title.props.style as TextStyle | readonly TextStyle[]) as TextStyle | undefined;
  return style?.color as string | undefined;
}

describe('tema, através do componente de verdade', () => {
  it('esquema claro (padrão): a cor do título é a do tema claro', async () => {
    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()}>
        <PitacoSurveyContent />
      </PitacoPreviewProvider>,
    );
    expect(titleColor()).toBe(DEFAULT_LIGHT_THEME.colors.textPrimary);
  });

  it('esquema escuro forçado: a cor do título é a do tema escuro', async () => {
    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()} theme={{ colorScheme: 'dark' }}>
        <PitacoSurveyContent />
      </PitacoPreviewProvider>,
    );
    expect(titleColor()).toBe(DEFAULT_DARK_THEME.colors.textPrimary);
  });

  it('token de cor inválido degrada para o padrão daquele token, sem quebrar a UI, com aviso', async () => {
    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()} theme={{ light: { colors: { textPrimary: 'não é cor' } } }}>
        <PitacoSurveyContent />
      </PitacoPreviewProvider>,
    );
    expect(titleColor()).toBe(DEFAULT_LIGHT_THEME.colors.textPrimary);
    expect(screen.getByText(/O quanto você recomendaria/)).toBeTruthy();
    expect(warn).toHaveBeenCalled();
  });
});

describe('textos substituídos', () => {
  it('um rótulo substituído aparece na UI de verdade, o resto continua no padrão', async () => {
    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()} strings={{ next: 'Avançar', closeA11yLabel: 'Sair da pesquisa' }}>
        <PitacoSurveyContent />
      </PitacoPreviewProvider>,
    );
    expect(screen.getByRole('button', { name: 'Avançar' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Próxima' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Sair da pesquisa' })).toBeTruthy();
    // Não substituído: continua em pt-BR padrão.
    expect(screen.getByText(/Pergunta 1 de/)).toBeTruthy();
  });
});

describe('cada slot substituído', () => {
  it('Header substituído', async () => {
    function CustomHeader(_props: HeaderSlotProps) {
      return <Text>cabeçalho próprio</Text>;
    }
    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()}>
        <PitacoSurveyContent slots={{ Header: CustomHeader }} />
      </PitacoPreviewProvider>,
    );
    expect(screen.getByText('cabeçalho próprio')).toBeTruthy();
    expect(screen.queryByText(/Pergunta 1 de/)).toBeNull();
  });

  it('Progress substituído', async () => {
    function CustomProgress(_props: ProgressSlotProps) {
      return <Text>progresso próprio</Text>;
    }
    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()}>
        <PitacoSurveyContent slots={{ Progress: CustomProgress }} />
      </PitacoPreviewProvider>,
    );
    expect(screen.getByText('progresso próprio')).toBeTruthy();
    expect(screen.queryByRole('progressbar')).toBeNull();
  });

  it('Footer substituído', async () => {
    function CustomFooter(_props: FooterSlotProps) {
      return <Text>rodapé próprio</Text>;
    }
    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()}>
        <PitacoSurveyContent slots={{ Footer: CustomFooter }} />
      </PitacoPreviewProvider>,
    );
    expect(screen.getByText('rodapé próprio')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Próxima' })).toBeNull();
  });

  it('CloseButton substituído', async () => {
    function CustomClose(_props: CloseButtonSlotProps) {
      return <Text>fechar próprio</Text>;
    }
    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()}>
        <PitacoSurveyContent slots={{ CloseButton: CustomClose }} />
      </PitacoPreviewProvider>,
    );
    expect(screen.getByText('fechar próprio')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Fechar pesquisa' })).toBeNull();
  });

  it('ThankYou substituído', async () => {
    function CustomThankYou(_props: ThankYouSlotProps) {
      return <Text>valeu!</Text>;
    }
    const onFinish = jest.fn();
    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()}>
        <PitacoSurveyContent slots={{ ThankYou: CustomThankYou }} onFinish={onFinish} />
      </PitacoPreviewProvider>,
    );
    await act(async () => fireEvent.press(screen.getByRole('radio', { name: '9' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    await act(async () => fireEvent.press(screen.getByRole('radio', { name: '4 de 5' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Enviar' })));
    expect(screen.getByText('valeu!')).toBeTruthy();
    expect(screen.queryByText('Obrigado!')).toBeNull();
  });
});

describe('renderizador por tipo substituído, através do componente de verdade', () => {
  it('um renderizador substituído desenha no lugar do padrão para aquele tipo, os outros continuam padrão', async () => {
    function CustomNps(props: QuestionRendererProps) {
      return <Text>NPS próprio: {String(props.value ?? '')}</Text>;
    }
    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()} renderers={{ nps: CustomNps }}>
        <PitacoSurveyContent />
      </PitacoPreviewProvider>,
    );
    expect(screen.getByText('NPS próprio: ')).toBeTruthy();
    expect(screen.queryByRole('radio', { name: '9' })).toBeNull();
  });
});

describe('renderizador/slot que lança erro: cai no padrão e emite o relatório', () => {
  it('renderizador substituído que lança erro cai no renderizador padrão e chama onRenderError', async () => {
    function BrokenNps(): ReactNode {
      throw new Error('renderizador quebrado de propósito');
    }
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    const onRenderError = jest.fn();
    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()} renderers={{ nps: BrokenNps }} onRenderError={onRenderError}>
        <PitacoSurveyContent />
      </PitacoPreviewProvider>,
    );
    // Caiu no renderizador padrão de NPS (pontos numéricos 0-10).
    expect(screen.getByRole('radio', { name: '9' })).toBeTruthy();
    expect(onRenderError).toHaveBeenCalledWith(expect.any(Error), { kind: 'renderer', name: 'nps' });
    errorSpy.mockRestore();
  });

  it('slot substituído que lança erro cai no slot padrão e chama onRenderError', async () => {
    function BrokenFooter(): ReactNode {
      throw new Error('slot quebrado de propósito');
    }
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    const onRenderError = jest.fn();
    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()} onRenderError={onRenderError}>
        <PitacoSurveyContent slots={{ Footer: BrokenFooter }} />
      </PitacoPreviewProvider>,
    );
    // Caiu no rodapé padrão.
    expect(screen.getByRole('button', { name: 'Próxima' })).toBeTruthy();
    expect(onRenderError).toHaveBeenCalledWith(expect.any(Error), { kind: 'slot', name: 'Footer' });
    errorSpy.mockRestore();
  });
});

describe('headless total: nenhum componente do Pitaco desenhado', () => {
  it('uma UI inteiramente própria, só sobre os hooks públicos, percorre e conclui a pesquisa', async () => {
    let latestSurvey: ReturnType<typeof usePitacoSurvey>;
    function OwnUi() {
      const survey = usePitacoSurvey();
      const theme = usePitacoTheme();
      const strings = usePitacoStrings();
      latestSurvey = survey;
      return (
        <>
          <Text>{theme.scheme}</Text>
          <Text>{strings.next}</Text>
          <Text testID="own-question">{survey.question?.statement ?? ''}</Text>
          <Text testID="own-status">{survey.status}</Text>
        </>
      );
    }
    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()}>
        <OwnUi />
      </PitacoPreviewProvider>,
    );
    // Nada da UI padrão foi desenhado: nenhum botão, nenhum papel de rádio.
    expect(screen.queryByRole('button')).toBeNull();
    expect(screen.queryByRole('radio')).toBeNull();
    expect(screen.getByTestId('own-question').props.children).toContain('O quanto você recomendaria');

    await act(() => latestSurvey.present());
    await act(() => latestSurvey.select(9));
    await act(() => latestSurvey.next());
    await act(() => latestSurvey.next());
    await act(() => latestSurvey.select(4));
    await act(() => latestSurvey.next());
    await act(() => latestSurvey.next());

    expect(screen.getByTestId('own-status').props.children).toBe('completed');
  });
});
