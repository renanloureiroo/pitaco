import { act, fireEvent, render, screen } from '@testing-library/react-native';
import { AccessibilityInfo } from 'react-native';
import type { SurveyQuestion } from '../../../core/survey/schema';
import { ScaleQuestion } from '../ScaleQuestion';
import { renderQuestion, rendererProps, requiredMissingError, themeFor } from './support';

// O schema de teste (`fixtures.ts`) não traz `SCALE` (fica nos testes de condição), então a
// pergunta é montada aqui, com faixa 1-7 e rótulos de ponta, como o guia pede para o tipo.
const question: SurveyQuestion = {
  key: 'scale-key',
  position: 1,
  statement: 'O quanto você concorda?',
  type: 'SCALE',
  required: true,
  options: [],
  range: { min: 1, max: 7, minLabel: 'Discordo totalmente', maxLabel: 'Concordo totalmente' },
  condition: null,
};

describe('<ScaleQuestion />', () => {
  it('desenha um ponto tocável por número da faixa e os rótulos nas pontas', async () => {
    await render(<ScaleQuestion {...rendererProps(question)} />);
    for (let point = 1; point <= 7; point += 1) {
      expect(screen.getByRole('radio', { name: String(point) })).toBeTruthy();
    }
    expect(screen.getByText('Discordo totalmente')).toBeTruthy();
    expect(screen.getByText('Concordo totalmente')).toBeTruthy();
  });

  it('tocar num ponto chama select com o número, nunca o rótulo de ponta', async () => {
    const props = rendererProps(question);
    await render(<ScaleQuestion {...props} />);
    await act(async () => fireEvent.press(screen.getByRole('radio', { name: '5' })));
    expect(props.actions.select).toHaveBeenCalledWith(5);
  });

  it('marca o ponto do valor atual como selecionado', async () => {
    const props = rendererProps(question, { value: 3 });
    await render(<ScaleQuestion {...props} />);
    expect(screen.getByRole('radio', { name: '3' }).props.accessibilityState).toEqual({ checked: true });
    expect(screen.getByRole('radio', { name: '4' }).props.accessibilityState).toEqual({ checked: false });
  });

  it('tema escuro aplica as cores do esquema escuro no ponto selecionado', async () => {
    const props = rendererProps(question, { value: 3, theme: themeFor('dark') });
    await renderQuestion(<ScaleQuestion {...props} />, 'dark');
    const point = screen.getByRole('radio', { name: '3' });
    const flat = [point.props.style as unknown].flat(Infinity) as Record<string, unknown>[];
    const style = flat.reduce((acc, item) => ({ ...acc, ...item }), {});
    expect(style.backgroundColor).toBe(props.theme.tokens.colors.primary);
  });

  it('erro de validação anuncia a mensagem ao leitor de tela', async () => {
    const announce = jest.spyOn(AccessibilityInfo, 'announceForAccessibility').mockImplementation(() => undefined);
    const props = rendererProps(question, { error: requiredMissingError(question) });
    await render(<ScaleQuestion {...props} />);
    expect(announce).toHaveBeenCalledWith(props.strings.validationRequired);
    announce.mockRestore();
  });

  it('layout quebra linha: os pontos ficam num container com flexWrap', async () => {
    await render(<ScaleQuestion {...rendererProps(question)} />);
    const point = screen.getByRole('radio', { name: '1' });
    // O pai imediato dos pontos é a `View` com `flexWrap: 'wrap'` de `NumericScale`.
    const row = point.parent;
    const flat = [row?.props.style as unknown].flat(Infinity) as Record<string, unknown>[];
    const style = flat.reduce((acc, item) => ({ ...acc, ...item }), {});
    expect(style.flexWrap).toBe('wrap');
  });

  it('alvo de toque de ao menos 44 pt por ponto', async () => {
    await render(<ScaleQuestion {...rendererProps(question)} />);
    const point = screen.getByRole('radio', { name: '1' });
    const flat = [point.props.style as unknown].flat(Infinity) as Record<string, unknown>[];
    const style = flat.reduce((acc, item) => ({ ...acc, ...item }), {});
    expect(style.minWidth as number).toBeGreaterThanOrEqual(44);
    expect(style.minHeight as number).toBeGreaterThanOrEqual(44);
  });
});
