import { act, fireEvent, render, screen } from '@testing-library/react-native';
import { AccessibilityInfo } from 'react-native';
import { referenceSurvey, KEYS } from '../../../__tests__/support/fixtures';
import { MultipleChoiceQuestion } from '../MultipleChoiceQuestion';
import { renderQuestion, rendererProps, requiredMissingError, themeFor } from './support';

const question = referenceSurvey().questions.find((item) => item.key === KEYS.features)!;

describe('<MultipleChoiceQuestion />', () => {
  it('lista as opções com papel checkbox, nenhuma marcada de início', async () => {
    await render(<MultipleChoiceQuestion {...rendererProps(question)} />);
    expect(screen.getByRole('checkbox', { name: 'Pix' }).props.accessibilityState).toEqual({ checked: false });
  });

  it('tocar numa opção não marcada chama select com o value dela', async () => {
    const props = rendererProps(question);
    await render(<MultipleChoiceQuestion {...props} />);
    await act(async () => fireEvent.press(screen.getByRole('checkbox', { name: 'Pix' })));
    expect(props.actions.select).toHaveBeenCalledWith('pix');
    expect(props.actions.deselect).not.toHaveBeenCalled();
  });

  it('tocar numa opção já marcada chama deselect com o value dela', async () => {
    const props = rendererProps(question, { value: ['pix', 'cartao'] });
    await render(<MultipleChoiceQuestion {...props} />);
    expect(screen.getByRole('checkbox', { name: 'Pix' }).props.accessibilityState).toEqual({ checked: true });
    await act(async () => fireEvent.press(screen.getByRole('checkbox', { name: 'Pix' })));
    expect(props.actions.deselect).toHaveBeenCalledWith('pix');
    expect(props.actions.select).not.toHaveBeenCalled();
  });

  it('tema escuro aplica as cores do esquema escuro', async () => {
    const props = rendererProps(question, { theme: themeFor('dark'), value: ['pix'] });
    await renderQuestion(<MultipleChoiceQuestion {...props} />, 'dark');
    const pix = screen.getByRole('checkbox', { name: 'Pix' });
    const flat = [pix.props.style as unknown].flat(Infinity) as Record<string, unknown>[];
    const style = flat.reduce((acc, item) => ({ ...acc, ...item }), {});
    expect(style.backgroundColor).toBe(props.theme.tokens.colors.primary);
  });

  it('erro de validação anuncia a mensagem ao leitor de tela', async () => {
    const announce = jest.spyOn(AccessibilityInfo, 'announceForAccessibility').mockImplementation(() => undefined);
    const props = rendererProps(question, { error: requiredMissingError(question) });
    await render(<MultipleChoiceQuestion {...props} />);
    expect(announce).toHaveBeenCalledWith(props.strings.validationRequired);
    announce.mockRestore();
  });

  it('alvo de toque de ao menos 44 pt', async () => {
    await render(<MultipleChoiceQuestion {...rendererProps(question)} />);
    const pix = screen.getByRole('checkbox', { name: 'Pix' });
    const flat = [pix.props.style as unknown].flat(Infinity) as Record<string, unknown>[];
    const style = flat.reduce((acc, item) => ({ ...acc, ...item }), {});
    expect(style.minHeight as number).toBeGreaterThanOrEqual(44);
  });
});
