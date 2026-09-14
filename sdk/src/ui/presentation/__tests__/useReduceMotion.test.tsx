import { act, render } from '@testing-library/react-native';
import { AccessibilityInfo } from 'react-native';
import { useReduceMotion } from '../useReduceMotion';

function Probe(props: { readonly onValue: (value: boolean) => void }) {
  props.onValue(useReduceMotion());
  return null;
}

describe('useReduceMotion', () => {
  it('começa em falso e assume o valor inicial do sistema assim que ele chega', async () => {
    jest.spyOn(AccessibilityInfo, 'isReduceMotionEnabled').mockResolvedValue(true);
    const values: boolean[] = [];
    await render(<Probe onValue={(value) => values.push(value)} />);
    expect(values[0]).toBe(false);
    expect(values.at(-1)).toBe(true);
  });

  it('acompanha o evento `reduceMotionChanged`', async () => {
    jest.spyOn(AccessibilityInfo, 'isReduceMotionEnabled').mockResolvedValue(false);
    let listener: ((value: boolean) => void) | undefined;
    jest.spyOn(AccessibilityInfo, 'addEventListener').mockImplementation((_event, handler) => {
      listener = handler;
      return { remove: jest.fn() };
    });
    const values: boolean[] = [];
    await render(<Probe onValue={(value) => values.push(value)} />);
    expect(values.at(-1)).toBe(false);
    await act(() => listener?.(true));
    expect(values.at(-1)).toBe(true);
  });

  it('nunca derruba o componente quando a API falha', async () => {
    jest.spyOn(AccessibilityInfo, 'isReduceMotionEnabled').mockRejectedValue(new Error('indisponível'));
    const values: boolean[] = [];
    await render(<Probe onValue={(value) => values.push(value)} />);
    expect(values.at(-1)).toBe(false);
  });
});
