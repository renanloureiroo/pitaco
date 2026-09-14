// Apresentação `'modal'`: tela cheia, com o mesmo contrato de fases (`onOpened`/`onClosed`) do
// bottom sheet, mas sem arrastar (não é o gesto natural de uma tela cheia). Mesmas vias que o
// contêiner controla: `hardware_back`, `programmatic` (dispensa vinda de fora enquanto aberto);
// `close_button` já chega pelo `CloseButton` de dentro de `<PitacoSurveyContent />` (fase 3a).

import { type ReactNode, useEffect, useRef, useState } from 'react';
import { Animated, BackHandler, KeyboardAvoidingView, Modal, Platform, StyleSheet, View } from 'react-native';
import type { DismissVia } from '../../catalog/events';
import type { PitacoResolvedTheme } from '../theme/tokens';
import type { EdgeInsets } from '../types';
import { useReduceMotion } from './useReduceMotion';
import { keyboardAvoidingBehavior } from './keyboard';

export interface FullScreenModalProps {
  readonly open: boolean;
  readonly onOpened: () => void;
  readonly onClosed: () => void;
  readonly onDismiss: (via: DismissVia) => void;
  readonly insets: EdgeInsets;
  readonly theme: PitacoResolvedTheme;
  readonly children: ReactNode;
}

type Phase = 'hidden' | 'entering' | 'visible' | 'leaving';

const OPEN_DURATION_MS = 220;
const CLOSE_DURATION_MS = 180;
const REDUCED_DURATION_MS = 1;

export function FullScreenModal(props: FullScreenModalProps): ReactNode {
  const { open, onOpened, onClosed, onDismiss, insets, theme, children } = props;
  const reduceMotion = useReduceMotion();

  // Instância única, inicializada de forma preguiçosa (ver o mesmo comentário em `BottomSheet`).
  const [opacity] = useState(() => new Animated.Value(0));

  // Inicializado a partir da própria prop (ver o mesmo comentário em `BottomSheet` sobre o
  // inicializador preguiçoso ser necessário quando `open` já chega `true` de saída).
  const [phase, setPhase] = useState<Phase>(() => (open ? 'entering' : 'hidden'));
  // Ajusta `phase` a partir da prop `open` durante o render, não dentro de um `useEffect` (ver o
  // mesmo comentário em `BottomSheet` sobre `react-hooks/set-state-in-effect`).
  const [trackedOpen, setTrackedOpen] = useState(open);
  if (open !== trackedOpen) {
    setTrackedOpen(open);
    if (open && phase === 'hidden') setPhase('entering');
    if (!open && (phase === 'entering' || phase === 'visible')) setPhase('leaving');
  }

  const callbacksRef = useRef({ onOpened, onClosed, onDismiss });
  const phaseRef = useRef(phase);
  useEffect(() => {
    callbacksRef.current = { onOpened, onClosed, onDismiss };
    phaseRef.current = phase;
  });

  const animateTo = (toValue: number, onFinish?: () => void) => {
    const duration = reduceMotion ? REDUCED_DURATION_MS : toValue === 1 ? OPEN_DURATION_MS : CLOSE_DURATION_MS;
    Animated.timing(opacity, { toValue, duration, useNativeDriver: true }).start(({ finished }) => {
      if (finished) onFinish?.();
    });
  };

  useEffect(() => {
    if (phase === 'entering') {
      animateTo(1, () => {
        setPhase('visible');
        callbacksRef.current.onOpened();
      });
    } else if (phase === 'leaving') {
      animateTo(0, () => {
        setPhase('hidden');
        callbacksRef.current.onClosed();
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase]);

  function startExit(via: DismissVia) {
    if (phaseRef.current === 'leaving' || phaseRef.current === 'hidden') return;
    phaseRef.current = 'leaving';
    setPhase('leaving');
    callbacksRef.current.onDismiss(via);
    animateTo(0, () => {
      setPhase('hidden');
      callbacksRef.current.onClosed();
    });
  }

  useEffect(() => {
    if (Platform.OS !== 'android') return undefined;
    if (phase !== 'entering' && phase !== 'visible') return undefined;
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      startExit('hardware_back');
      return true;
    });
    return () => subscription.remove();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase]);

  if (phase === 'hidden') return null;

  return (
    <Modal
      transparent={false}
      visible
      animationType="none"
      presentationStyle={Platform.OS === 'ios' ? 'fullScreen' : undefined}
      statusBarTranslucent={Platform.OS === 'android'}
      onRequestClose={() => startExit('hardware_back')}
    >
      <Animated.View style={[styles.fill, { backgroundColor: theme.tokens.colors.background, opacity }]}>
        {/* Teclado: `padding` no iOS e `height` no Android (ver `keyboard.ts`). */}
        <KeyboardAvoidingView
          testID="pitaco-fullscreen-keyboard-avoider"
          behavior={keyboardAvoidingBehavior()}
          style={styles.fill}
        >
          <View
            testID="pitaco-fullscreen-safe-area"
            style={[
              styles.safeArea,
              {
                paddingTop: insets.top,
                paddingBottom: insets.bottom,
                paddingLeft: insets.left,
                paddingRight: insets.right,
              },
            ]}
          >
            {children}
          </View>
        </KeyboardAvoidingView>
      </Animated.View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  fill: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
  },
});
