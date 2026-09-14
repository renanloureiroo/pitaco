import { act, fireEvent, render, screen } from '@testing-library/react-native';
import { AccessibilityInfo } from 'react-native';
import { referenceSurvey, KEYS } from '../../../__tests__/support/fixtures';
import { RatingQuestion } from '../RatingQuestion';
import { rendererProps, requiredMissingError, themeFor } from './support';

// Faixa 1-5, obrigatória.
const question = referenceSurvey().questions.find((item) => item.key === KEYS.rating)!;

describe('<RatingQuestion />', () => {
  it('desenha uma estrela por ponto da faixa, com rótulo "X de N"', async () => {
    await render(<RatingQuestion {...rendererProps(question)} />);
    for (let position = 1; position <= 5; position += 1) {
      expect(screen.getByRole('radio', { name: `${position} de 5` })).toBeTruthy();
    }
  });

  it('tocar numa estrela chama select com o número da faixa, nunca um rótulo', async () => {
    const props = rendererProps(question);
    await render(<RatingQuestion {...props} />);
    await act(async () => fireEvent.press(screen.getByRole('radio', { name: '4 de 5' })));
    expect(props.actions.select).toHaveBeenCalledWith(4);
  });

  it('marca como selecionada só a estrela do valor atual', async () => {
    const props = rendererProps(question, { value: 3 });
    await render(<RatingQuestion {...props} />);
    expect(screen.getByRole('radio', { name: '3 de 5' }).props.accessibilityState).toEqual({ checked: true });
    expect(screen.getByRole('radio', { name: '4 de 5' }).props.accessibilityState).toEqual({ checked: false });
  });

  it('tema escuro aplica as cores do esquema escuro na estrela preenchida', async () => {
    const props = rendererProps(question, { value: 1, theme: themeFor('dark') });
    await render(<RatingQuestion {...props} />);
    const filled = screen.getByText('★');
    const flat = [filled.props.style as unknown].flat(Infinity) as Record<string, unknown>[];
    const style = flat.reduce((acc, item) => ({ ...acc, ...item }), {});
    expect(style.color).toBe(props.theme.tokens.colors.primary);
  });

  it('erro de validação anuncia a mensagem ao leitor de tela', async () => {
    const announce = jest.spyOn(AccessibilityInfo, 'announceForAccessibility').mockImplementation(() => undefined);
    const props = rendererProps(question, { error: requiredMissingError(question) });
    await render(<RatingQuestion {...props} />);
    expect(announce).toHaveBeenCalledWith(props.strings.validationRequired);
    announce.mockRestore();
  });

  it('alvo de toque de ao menos 44 pt por estrela', async () => {
    await render(<RatingQuestion {...rendererProps(question)} />);
    const star = screen.getByRole('radio', { name: '1 de 5' });
    const flat = [star.props.style as unknown].flat(Infinity) as Record<string, unknown>[];
    const style = flat.reduce((acc, item) => ({ ...acc, ...item }), {});
    expect(style.minWidth as number).toBeGreaterThanOrEqual(44);
    expect(style.minHeight as number).toBeGreaterThanOrEqual(44);
  });
});
