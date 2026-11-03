// Escala de fonte do sistema respeitada sem quebrar o layout: o título da pergunta (o único
// `Text` cru de `<PitacoSurveyContent />` — precisa de uma ref de verdade para o foco de
// acessibilidade, então não passa por `PitacoText`) e o campo de texto livre usam o mesmo teto
// (`DEFAULT_MAX_FONT_SIZE_MULTIPLIER`) que `PitacoText` já aplica a todo o resto da UI padrão.

import { act, fireEvent, render, screen } from '@testing-library/react-native';
import { deliverableSurvey } from '../../../__tests__/support/fixtures';
import { PitacoPreviewProvider } from '../../../preview';
import { DEFAULT_MAX_FONT_SIZE_MULTIPLIER } from '../../primitives/PitacoText';
import { PitacoSurveyContent } from '../PitacoSurveyContent';

const NOW = Date.UTC(2026, 8, 12, 13, 0, 0);

beforeEach(() => {
  jest.useFakeTimers({ now: NOW });
});

afterEach(() => {
  jest.useRealTimers();
});

describe('teto de escala de fonte, coerente em toda a UI padrão', () => {
  it('o título da pergunta tem o mesmo teto que os demais textos da UI padrão', async () => {
    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()}>
        <PitacoSurveyContent />
      </PitacoPreviewProvider>,
    );
    expect(screen.getByRole('header').props.maxFontSizeMultiplier).toBe(DEFAULT_MAX_FONT_SIZE_MULTIPLIER);
  });

  it('o campo de texto livre (última pergunta) também respeita o mesmo teto', async () => {
    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()}>
        <PitacoSurveyContent />
      </PitacoPreviewProvider>,
    );
    await act(async () => fireEvent.press(screen.getByRole('radio', { name: '9' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    await act(async () => fireEvent.press(screen.getByRole('radio', { name: '4 de 5' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));

    const field = screen.getByPlaceholderText('Digite sua resposta');
    expect(field.props.maxFontSizeMultiplier).toBe(DEFAULT_MAX_FONT_SIZE_MULTIPLIER);
  });
});
