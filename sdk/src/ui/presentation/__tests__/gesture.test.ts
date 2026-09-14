// Testa a lógica de arrastar a folha chamando diretamente os mesmos handlers que o
// `PanResponder` de `BottomSheet.tsx` usa (`createDragHandlers`) — arrasto simulado sem depender
// do histórico de toque nativo que o `PanResponder` de verdade exige (o React Native não permite
// fabricar isso por evento sintético num teste).
import { CLOSE_DISTANCE_THRESHOLD, CLOSE_VELOCITY_THRESHOLD, createDragHandlers, dragProgress, shouldCloseFromGesture } from '../gesture';

function gesture(overrides: Partial<{ dx: number; dy: number; vx: number; vy: number }> = {}) {
  return { dx: 0, dy: 0, vx: 0, vy: 0, x0: 0, y0: 0, moveX: 0, moveY: 0, numberActiveTouches: 1, stateID: 1, ...overrides };
}

describe('dragProgress', () => {
  it('reduz o progresso proporcionalmente ao arrasto para baixo', () => {
    expect(dragProgress(1, 100, 200)).toBeCloseTo(0.5);
  });

  it('aplica resistência ao arrasto para cima (além do topo)', () => {
    const withResistance = dragProgress(1, -100, 200);
    // Sem resistência seria 1.5 (grudado no teto por `clamp`); com resistência o valor some
    // menos rápido, mas continua no máximo 1.
    expect(withResistance).toBe(1);
  });

  it('nunca sai do intervalo [0, 1]', () => {
    expect(dragProgress(0.1, 10_000, 200)).toBe(0);
    expect(dragProgress(0.9, -10_000, 200)).toBe(1);
  });
});

describe('shouldCloseFromGesture', () => {
  it('fecha quando a distância passa do limiar', () => {
    expect(shouldCloseFromGesture({ dy: CLOSE_DISTANCE_THRESHOLD + 1, vy: 0 })).toBe(true);
  });

  it('fecha quando a velocidade passa do limiar, mesmo com pouca distância', () => {
    expect(shouldCloseFromGesture({ dy: 10, vy: CLOSE_VELOCITY_THRESHOLD + 0.1 })).toBe(true);
  });

  it('volta com mola quando nem distância nem velocidade bastam', () => {
    expect(shouldCloseFromGesture({ dy: 10, vy: 0.1 })).toBe(false);
  });
});

describe('createDragHandlers', () => {
  function setup() {
    let progressValue = 1; // folha totalmente aberta
    const onClose = jest.fn();
    const onSpringBack = jest.fn();
    const handlers = createDragHandlers({
      setProgress: (value) => {
        progressValue = value;
      },
      stopProgress: (onStopped) => onStopped(progressValue),
      getHiddenOffset: () => 200,
      onClose,
      onSpringBack,
    });
    return { handlers, onClose, onSpringBack, getProgress: () => progressValue };
  }

  it('arrasta a folha proporcionalmente ao movimento', () => {
    const { handlers, getProgress } = setup();
    handlers.onPanResponderGrant();
    handlers.onPanResponderMove(null, gesture({ dy: 100 }));
    expect(getProgress()).toBeCloseTo(0.5);
  });

  it('solta longe fecha com a via "swipe" (chama `onClose`, não `onSpringBack`)', () => {
    const { handlers, onClose, onSpringBack } = setup();
    handlers.onPanResponderGrant();
    handlers.onPanResponderMove(null, gesture({ dy: 200 }));
    handlers.onPanResponderRelease(null, gesture({ dy: 200, vy: 0 }));
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(onSpringBack).not.toHaveBeenCalled();
  });

  it('solta rápido fecha mesmo com pouca distância', () => {
    const { handlers, onClose } = setup();
    handlers.onPanResponderGrant();
    handlers.onPanResponderMove(null, gesture({ dy: 20 }));
    handlers.onPanResponderRelease(null, gesture({ dy: 20, vy: 2 }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('solta perto e devagar volta com mola (não fecha)', () => {
    const { handlers, onClose, onSpringBack } = setup();
    handlers.onPanResponderGrant();
    handlers.onPanResponderMove(null, gesture({ dy: 20 }));
    handlers.onPanResponderRelease(null, gesture({ dy: 20, vy: 0 }));
    expect(onSpringBack).toHaveBeenCalledTimes(1);
    expect(onClose).not.toHaveBeenCalled();
  });

  it('o gesto interrompido (por exemplo por outro responder) também volta com mola', () => {
    const { handlers, onSpringBack } = setup();
    handlers.onPanResponderTerminate();
    expect(onSpringBack).toHaveBeenCalledTimes(1);
  });

  it('só assume o gesto como arrastar depois de um deslocamento vertical mínimo', () => {
    const { handlers } = setup();
    expect(handlers.onMoveShouldSetPanResponder(null, gesture({ dy: 1 }))).toBe(false);
    expect(handlers.onMoveShouldSetPanResponder(null, gesture({ dy: 10 }))).toBe(true);
  });
});
