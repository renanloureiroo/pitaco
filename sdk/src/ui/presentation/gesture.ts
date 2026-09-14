// Lógica de arrastar a folha do bottom sheet: solta longe ou rápido demais fecha (via `'swipe'`),
// senão volta com mola. Separada do componente para ser testável direto — o `PanResponder` do
// React Native computa a `gestureState` a partir do histórico real de toque; não há como
// fabricar isso por evento sintético num teste. Os testes chamam estas funções (as mesmas que o
// `PanResponder` de `BottomSheet.tsx` usa) diretamente, com uma `gestureState` de mentira.

import type { PanResponderGestureState } from 'react-native';

export const CLOSE_DISTANCE_THRESHOLD = 120;
export const CLOSE_VELOCITY_THRESHOLD = 0.8;
export const UPWARD_RESISTANCE = 0.25;
// Deslocamento mínimo para o gesto ser reconhecido como arrastar (e não um toque comum).
export const MOVE_ACTIVATION_THRESHOLD = 4;

export function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

// Progresso (0 a 1, escondido a aberto) depois de arrastar `dy` a partir de `startProgress`.
// Resistência para cima (`dy < 0`): a folha já está totalmente aberta, então "puxar" além do
// topo anda bem menos que o dedo.
export function dragProgress(startProgress: number, dy: number, hiddenOffset: number): number {
  const resisted = dy >= 0 ? dy : dy * UPWARD_RESISTANCE;
  if (hiddenOffset <= 0) return startProgress;
  return clamp(startProgress - resisted / hiddenOffset, 0, 1);
}

// Solta longe (`dy` maior que o limiar de distância) ou rápido (`vy` maior que o limiar de
// velocidade, mesmo com pouca distância ainda percorrida) fecha; qualquer outro caso volta com
// mola para o estado aberto.
export function shouldCloseFromGesture(gesture: Pick<PanResponderGestureState, 'dy' | 'vy'>): boolean {
  return gesture.dy > CLOSE_DISTANCE_THRESHOLD || gesture.vy > CLOSE_VELOCITY_THRESHOLD;
}

// As mesmas referências que o componente já mantém atualizadas via ref (nunca variável de
// render): o valor animado (estável durante toda a vida do componente), o progresso no início do
// arrasto e a altura escondida mais recente.
export interface GestureController {
  readonly setProgress: (value: number) => void;
  readonly stopProgress: (onStopped: (value: number) => void) => void;
  readonly getHiddenOffset: () => number;
  readonly onClose: () => void;
  readonly onSpringBack: () => void;
}

export interface DragHandlers {
  readonly onStartShouldSetPanResponder: () => boolean;
  readonly onMoveShouldSetPanResponder: (event: unknown, gesture: PanResponderGestureState) => boolean;
  readonly onPanResponderGrant: () => void;
  readonly onPanResponderMove: (event: unknown, gesture: PanResponderGestureState) => void;
  readonly onPanResponderRelease: (event: unknown, gesture: PanResponderGestureState) => void;
  readonly onPanResponderTerminate: () => void;
}

export function createDragHandlers(controller: GestureController): DragHandlers {
  let dragStartProgress = 0;
  return {
    onStartShouldSetPanResponder: () => true,
    onMoveShouldSetPanResponder: (_event, gesture) => Math.abs(gesture.dy) > MOVE_ACTIVATION_THRESHOLD,
    onPanResponderGrant: () => {
      controller.stopProgress((value) => {
        dragStartProgress = value;
      });
    },
    onPanResponderMove: (_event, gesture) => {
      controller.setProgress(dragProgress(dragStartProgress, gesture.dy, controller.getHiddenOffset()));
    },
    onPanResponderRelease: (_event, gesture) => {
      if (shouldCloseFromGesture(gesture)) controller.onClose();
      else controller.onSpringBack();
    },
    onPanResponderTerminate: () => controller.onSpringBack(),
  };
}
