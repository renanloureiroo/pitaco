import { act, fireEvent, render, screen } from '@testing-library/react-native';
import { AccessibilityInfo } from 'react-native';
import { referenceSurvey, KEYS } from '../../../__tests__/support/fixtures';
import { NpsQuestion } from '../NpsQuestion';
import { renderQuestion, rendererProps, requiredMissingError, themeFor } from './support';
import type { SurveyQuestion } from '../../../core/survey/schema';

// Faixa 0-10, obrigatória, com rótulos de ponta próprios.
const question = referenceSurvey().questions.find((item) => item.key === KEYS.nps)!;

describe('<NpsQuestion />', () => {
  it('desenha os 11 pontos de 0 a 10 com os rótulos de ponta do schema', async () => {
    await render(<NpsQuestion {...rendererProps(question)} />);
    for (let point = 0; point <= 10; point += 1) {
      expect(screen.getByRole('radio', { name: String(point) })).toBeTruthy();
    }
    expect(screen.getByText('Nada provável')).toBeTruthy();
    expect(screen.getByText('Muito provável')).toBeTruthy();
  });

  it('sem rótulo de ponta no schema, usa o padrão de strings', async () => {
    const withoutRange: SurveyQuestion = { ...question, range: null };
    await render(<NpsQuestion {...rendererProps(withoutRange)} />);
    expect(screen.getByText('Nada provável')).toBeTruthy();
    expect(screen.getByText('Muito provável')).toBeTruthy();
  });

  it('tocar num ponto chama select com o número, nunca o rótulo', async () => {
    const props = rendererProps(question);
    await render(<NpsQuestion {...props} />);
    await act(async () => fireEvent.press(screen.getByRole('radio', { name: '9' })));
    expect(props.actions.select).toHaveBeenCalledWith(9);
  });

  it('marca o ponto do valor atual como selecionado', async () => {
    const props = rendererProps(question, { value: 7 });
    await render(<NpsQuestion {...props} />);
    expect(screen.getByRole('radio', { name: '7' }).props.accessibilityState).toEqual({ checked: true });
    expect(screen.getByRole('radio', { name: '8' }).props.accessibilityState).toEqual({ checked: false });
  });

  it('tema escuro aplica as cores do esquema escuro no ponto selecionado', async () => {
    const props = rendererProps(question, { value: 5, theme: themeFor('dark') });
    await renderQuestion(<NpsQuestion {...props} />, 'dark');
    const point = screen.getByRole('radio', { name: '5' });
    const flat = [point.props.style as unknown].flat(Infinity) as Record<string, unknown>[];
    const style = flat.reduce((acc, item) => ({ ...acc, ...item }), {});
    expect(style.backgroundColor).toBe(props.theme.tokens.colors.primary);
  });

  it('erro de validação anuncia a mensagem ao leitor de tela', async () => {
    const announce = jest.spyOn(AccessibilityInfo, 'announceForAccessibility').mockImplementation(() => undefined);
    const props = rendererProps(question, { error: requiredMissingError(question) });
    await render(<NpsQuestion {...props} />);
    expect(announce).toHaveBeenCalledWith(props.strings.validationRequired);
    announce.mockRestore();
  });

  it('alvo de toque de ao menos 44 pt por ponto', async () => {
    await render(<NpsQuestion {...rendererProps(question)} />);
    const point = screen.getByRole('radio', { name: '0' });
    const flat = [point.props.style as unknown].flat(Infinity) as Record<string, unknown>[];
    const style = flat.reduce((acc, item) => ({ ...acc, ...item }), {});
    expect(style.minWidth as number).toBeGreaterThanOrEqual(44);
    expect(style.minHeight as number).toBeGreaterThanOrEqual(44);
  });
});
