import { act, fireEvent, render, screen } from '@testing-library/react-native';
import { AccessibilityInfo } from 'react-native';
import { referenceSurvey, KEYS } from '../../../__tests__/support/fixtures';
import { MAX_FREE_TEXT_LENGTH } from '../../../core/survey/answers';
import { FreeTextQuestion } from '../FreeTextQuestion';
import { rendererProps, requiredMissingError, themeFor } from './support';

const question = referenceSurvey().questions.find((item) => item.key === KEYS.comment)!;

describe('<FreeTextQuestion />', () => {
  it('campo multilinha ligado a setText/focusText/blurText do core', async () => {
    const props = rendererProps(question);
    await render(<FreeTextQuestion {...props} />);
    const input = screen.getByLabelText(props.strings.freeTextPlaceholder);

    await act(async () => fireEvent(input, 'focus'));
    expect(props.actions.focusText).toHaveBeenCalledTimes(1);

    await act(async () => fireEvent.changeText(input, 'olá'));
    expect(props.actions.setText).toHaveBeenCalledWith('olá');

    await act(async () => fireEvent(input, 'blur'));
    expect(props.actions.blurText).toHaveBeenCalledTimes(1);
  });

  it('nunca emite evento nem chama ação com nada além do próprio texto — sem side-channel', async () => {
    const props = rendererProps(question);
    await render(<FreeTextQuestion {...props} />);
    await act(async () => fireEvent.changeText(screen.getByLabelText(props.strings.freeTextPlaceholder), 'texto qualquer'));
    expect(props.actions.select).not.toHaveBeenCalled();
    expect(props.actions.deselect).not.toHaveBeenCalled();
  });

  it('mostra o contador de caracteres contra o teto do core', async () => {
    const props = rendererProps(question, { value: 'abcde' });
    await render(<FreeTextQuestion {...props} />);
    expect(screen.getByText(`5/${MAX_FREE_TEXT_LENGTH}`)).toBeTruthy();
  });

  it('limita a digitação ao teto do core via maxLength', async () => {
    const props = rendererProps(question);
    await render(<FreeTextQuestion {...props} />);
    const input = screen.getByLabelText(props.strings.freeTextPlaceholder);
    expect(input.props.maxLength).toBe(MAX_FREE_TEXT_LENGTH);
  });

  it('mostra o aviso de texto livre junto do campo e o anuncia ao leitor de tela', async () => {
    const announce = jest.spyOn(AccessibilityInfo, 'announceForAccessibility').mockImplementation(() => undefined);
    const props = rendererProps(question, { freeTextNotice: 'Evite escrever dados pessoais.' });
    await render(<FreeTextQuestion {...props} />);
    expect(screen.getByText('Evite escrever dados pessoais.')).toBeTruthy();
    expect(announce).toHaveBeenCalledWith('Evite escrever dados pessoais.');
    announce.mockRestore();
  });

  it('sem aviso configurado, não mostra nem anuncia nada', async () => {
    const announce = jest.spyOn(AccessibilityInfo, 'announceForAccessibility').mockImplementation(() => undefined);
    const props = rendererProps(question, { freeTextNotice: null });
    await render(<FreeTextQuestion {...props} />);
    expect(announce).not.toHaveBeenCalled();
    announce.mockRestore();
  });

  it('tema escuro aplica as cores do esquema escuro', async () => {
    const props = rendererProps(question, { theme: themeFor('dark') });
    await render(<FreeTextQuestion {...props} />);
    const input = screen.getByLabelText(props.strings.freeTextPlaceholder);
    const flat = [input.props.style as unknown].flat(Infinity) as Record<string, unknown>[];
    const style = flat.reduce((acc, item) => ({ ...acc, ...item }), {});
    expect(style.color).toBe(props.theme.tokens.colors.textPrimary);
  });

  it('erro de validação anuncia a mensagem ao leitor de tela', async () => {
    const announce = jest.spyOn(AccessibilityInfo, 'announceForAccessibility').mockImplementation(() => undefined);
    const props = rendererProps(question, { error: requiredMissingError(question) });
    await render(<FreeTextQuestion {...props} />);
    expect(announce).toHaveBeenCalledWith(props.strings.validationRequired);
    announce.mockRestore();
  });
});
