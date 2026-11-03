import { act, fireEvent, render, screen } from '@testing-library/react-native';
import { AccessibilityInfo } from 'react-native';
import { referenceSurvey, KEYS } from '../../../__tests__/support/fixtures';
import { SingleChoiceQuestion } from '../SingleChoiceQuestion';
import { renderQuestion, rendererProps, requiredMissingError, themeFor } from './support';

const question = referenceSurvey().questions.find((item) => item.key === KEYS.reason)!;

describe('<SingleChoiceQuestion />', () => {
  it('lista as opções com papel radio e nenhuma selecionada de início', async () => {
    await render(<SingleChoiceQuestion {...rendererProps(question)} />);
    const preco = screen.getByRole('radio', { name: 'Preço' });
    const atendimento = screen.getByRole('radio', { name: 'Atendimento' });
    expect(preco.props.accessibilityState).toEqual({ checked: false });
    expect(atendimento.props.accessibilityState).toEqual({ checked: false });
  });

  it('tocar numa opção chama select com o value da opção, nunca o rótulo', async () => {
    const props = rendererProps(question);
    await render(<SingleChoiceQuestion {...props} />);
    await act(async () => fireEvent.press(screen.getByRole('radio', { name: 'Preço' })));
    expect(props.actions.select).toHaveBeenCalledWith('preco');
    expect(props.actions.select).not.toHaveBeenCalledWith('Preço');
  });

  it('marca a opção já escolhida como selecionada e troca ao tocar noutra', async () => {
    const props = rendererProps(question, { value: 'preco' });
    await render(<SingleChoiceQuestion {...props} />);
    expect(screen.getByRole('radio', { name: 'Preço' }).props.accessibilityState).toEqual({ checked: true });

    await act(async () => fireEvent.press(screen.getByRole('radio', { name: 'Atendimento' })));
    expect(props.actions.select).toHaveBeenCalledWith('atendimento');
  });

  it('tema escuro aplica as cores do esquema escuro', async () => {
    const props = rendererProps(question, { theme: themeFor('dark') });
    await renderQuestion(<SingleChoiceQuestion {...props} />, 'dark');
    const preco = screen.getByRole('radio', { name: 'Preço' });
    const flat = [preco.props.style as unknown].flat(Infinity) as Record<string, unknown>[];
    const style = flat.reduce((acc, item) => ({ ...acc, ...item }), {});
    expect(style.borderColor).toBe(props.theme.tokens.colors.border);
  });

  it('erro de validação anuncia a mensagem ao leitor de tela', async () => {
    const announce = jest.spyOn(AccessibilityInfo, 'announceForAccessibility').mockImplementation(() => undefined);
    const props = rendererProps(question, { error: requiredMissingError(question) });
    await render(<SingleChoiceQuestion {...props} />);
    expect(announce).toHaveBeenCalledWith(props.strings.validationRequired);
    announce.mockRestore();
  });

  it('alvo de toque de ao menos 44 pt', async () => {
    await render(<SingleChoiceQuestion {...rendererProps(question)} />);
    const preco = screen.getByRole('radio', { name: 'Preço' });
    const flat = [preco.props.style as unknown].flat(Infinity) as Record<string, unknown>[];
    const style = flat.reduce((acc, item) => ({ ...acc, ...item }), {});
    expect(style.minHeight as number).toBeGreaterThanOrEqual(44);
  });
});
