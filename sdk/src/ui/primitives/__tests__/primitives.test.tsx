import { render, screen } from '@testing-library/react-native';
import { hitSlopFor, MIN_TOUCH_TARGET, PitacoButton } from '../PitacoButton';

describe('PitacoButton', () => {
  it('tem papel e rótulo de acessibilidade de botão', async () => {
    await render(<PitacoButton label="Próxima" onPress={() => undefined} />);
    const button = screen.getByRole('button', { name: 'Próxima' });
    expect(button).toBeTruthy();
  });

  it('usa o rótulo de acessibilidade explícito quando informado', async () => {
    await render(<PitacoButton label="×" accessibilityLabel="Fechar" onPress={() => undefined} />);
    expect(screen.getByRole('button', { name: 'Fechar' })).toBeTruthy();
  });

  it('marca o estado desabilitado', async () => {
    await render(<PitacoButton label="Enviar" disabled onPress={() => undefined} />);
    const button = screen.getByRole('button', { name: 'Enviar' });
    expect(button.props.accessibilityState).toEqual({ disabled: true });
  });

  it('garante alvo de toque de ao menos 44 pt via hitSlop quando o visual é menor', async () => {
    expect(hitSlopFor(44)).toBe(0);
    expect(hitSlopFor(30)).toBe(7); // (44 - 30) / 2, arredondado para cima
    expect(hitSlopFor(20, 44)).toBe(12);
  });

  it('o botão nasce com altura mínima de 44 pt', async () => {
    await render(<PitacoButton label="Voltar" onPress={() => undefined} />);
    const button = screen.getByRole('button', { name: 'Voltar' });
    const styles = [button.props.style as unknown].flat(Infinity) as Record<string, unknown>[];
    const flatStyle: Record<string, unknown> = styles.reduce((acc: Record<string, unknown>, style) => ({ ...acc, ...style }), {});
    expect(flatStyle.minHeight as number).toBeGreaterThanOrEqual(MIN_TOUCH_TARGET);
  });
});
