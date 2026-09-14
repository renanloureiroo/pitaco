// Bottom sheet próprio do Pitaco: só com o que o React Native já traz (restrição 1 — sem
// `@gorhom/bottom-sheet`, sem Reanimated, sem Gesture Handler). `Modal` transparente, `Animated`
// com `useNativeDriver: true` para entrada/saída, `PanResponder` na alça para arrastar e soltar.
//
// Este componente só cuida da moldura (folha, backdrop, arrastar, teclado, safe area, movimento
// reduzido). Quem decide *quando* abrir e como reagir a cada via de dispensa é `SurfaceHost`
// (`src/react/SurfaceHost.tsx`) — este componente só relata: `onOpened` quando a animação de
// entrada termina, `onClosed` quando a de saída termina, e `onDismiss(via)` para arrastar,
// backdrop, voltar do Android e a ação de acessibilidade da alça.

import { type ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import {
  Animated,
  BackHandler,
  Dimensions,
  KeyboardAvoidingView,
  Modal,
  PanResponder,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import type { DismissVia } from '../../catalog/events';
import type { PitacoResolvedTheme } from '../theme/tokens';
import type { PitacoStrings } from '../strings/strings';
import type { EdgeInsets } from '../types';
import { createDragHandlers } from './gesture';
import { keyboardAvoidingBehavior } from './keyboard';
import { useReduceMotion } from './useReduceMotion';

export interface BottomSheetProps {
  // `true` enquanto o contêiner deve estar visível (abrindo ou aberto); `SurfaceHost` só vira
  // `false` depois que o desfecho (conclusão ou dispensa) já aconteceu no core — este componente
  // continua montado durante a própria animação de saída mesmo com `open=false`.
  readonly open: boolean;
  readonly onOpened: () => void;
  readonly onClosed: () => void;
  readonly onDismiss: (via: DismissVia) => void;
  readonly insets: EdgeInsets;
  readonly theme: PitacoResolvedTheme;
  readonly strings: PitacoStrings;
  readonly children: ReactNode;
}

type Phase = 'hidden' | 'entering' | 'visible' | 'leaving';

const OPEN_DURATION_MS = 280;
const CLOSE_DURATION_MS = 220;
const REDUCED_DURATION_MS = 1;
const MAX_SHEET_HEIGHT_RATIO = 0.9;

export function BottomSheet(props: BottomSheetProps): ReactNode {
  const { open, onOpened, onClosed, onDismiss, insets, theme, strings, children } = props;
  const reduceMotion = useReduceMotion();

  const windowHeight = Dimensions.get('window').height;
  const hiddenOffset = windowHeight + 64;

  // Instância única do valor animado (nunca recriada): inicializada de forma preguiçosa via
  // `useState`, não `useRef(new Animated.Value(...)).current` — ler `.current` de um ref recém
  // construído durante o próprio render é o que o lint novo (`react-hooks/refs`) rejeita.
  const [progress] = useState(() => new Animated.Value(0));

  // Inicializado a partir da própria prop: se `open` já chega `true` na primeira renderização
  // (a pesquisa já estava disponível quando este componente nasceu), a folha começa direto
  // "entrando" — sem o inicializador preguiçoso, a comparação abaixo nunca veria uma "mudança"
  // nesse caso, porque `trackedOpen` já nasceria igual a `open`.
  const [phase, setPhase] = useState<Phase>(() => (open ? 'entering' : 'hidden'));
  // Sincroniza `phase` com a prop `open` durante o render (não num efeito): é o padrão que o
  // React recomenda para "ajustar estado quando uma prop muda" — chamar `setState` direto no
  // corpo de um `useEffect` é o que o lint novo (`react-hooks/set-state-in-effect`) rejeita.
  const [trackedOpen, setTrackedOpen] = useState(open);
  if (open !== trackedOpen) {
    setTrackedOpen(open);
    if (open && phase === 'hidden') setPhase('entering');
    if (!open && (phase === 'entering' || phase === 'visible')) setPhase('leaving');
  }

  // Callbacks e fase sempre atuais dentro dos handlers de gesto (que não são recriados a cada
  // render — ver `panResponder` abaixo), sem exigir que eles voltem a capturar nada.
  const callbacksRef = useRef({ onOpened, onClosed, onDismiss });
  const phaseRef = useRef(phase);
  useEffect(() => {
    callbacksRef.current = { onOpened, onClosed, onDismiss };
    phaseRef.current = phase;
  });

  const animateTo = (toValue: number, onFinish?: () => void) => {
    const duration = reduceMotion ? REDUCED_DURATION_MS : toValue === 1 ? OPEN_DURATION_MS : CLOSE_DURATION_MS;
    Animated.timing(progress, { toValue, duration, useNativeDriver: true }).start(({ finished }) => {
      if (finished) onFinish?.();
    });
  };

  // A própria animação (efeito colateral sobre um sistema externo, o driver nativo) só começa
  // aqui, reagindo à fase decidida acima; o `setPhase` de conclusão mora dentro do retorno da
  // animação (`.start(callback)`), não solto no corpo do efeito.
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

  function springBackToOpen() {
    phaseRef.current = 'visible';
    setPhase('visible');
    animateTo(1);
  }

  // Android: o voltar de hardware fecha como o backdrop fecharia. Registrado só enquanto visível
  // (a folha some sozinha; nada a fazer se já estiver fechando).
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

  // O `PanResponder` é criado uma única vez (`useMemo` com dependências vazias — `progress` e
  // `dragStartProgress` são estáveis por toda a vida do componente, e os handlers nunca fecham
  // sobre variável nenhuma do render além deles: só sobre outros refs, sempre mantidos atuais no
  // efeito acima (`hiddenOffsetRef`) e abaixo (`startExitRef`/`springBackToOpenRef`)). Recriar o
  // responder a cada render trocaria o gesto em andamento pelo de uma closure nova a cada toque.
  const hiddenOffsetRef = useRef(hiddenOffset);
  const startExitRef = useRef(startExit);
  const springBackToOpenRef = useRef(springBackToOpen);
  useEffect(() => {
    hiddenOffsetRef.current = hiddenOffset;
    startExitRef.current = startExit;
    springBackToOpenRef.current = springBackToOpen;
  });

  const panResponder = useMemo(
    () =>
      // Os handlers só leem `.current` de refs quando chamados por um gesto de verdade (nunca
      // durante o próprio render); o lint (`react-hooks/refs`) não consegue provar isso através
      // da chamada opaca `PanResponder.create`, e este é justo o padrão que ela existe para
      // habilitar — memoização estável cujos handlers leem sempre o estado mais recente.
      PanResponder.create(
        // eslint-disable-next-line react-hooks/refs
        createDragHandlers({
          setProgress: (value) => progress.setValue(value),
          stopProgress: (onStopped) => progress.stopAnimation(onStopped),
          getHiddenOffset: () => hiddenOffsetRef.current,
          onClose: () => startExitRef.current('swipe'),
          onSpringBack: () => springBackToOpenRef.current(),
        }),
      ),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  if (phase === 'hidden') return null;

  const translateY = progress.interpolate({ inputRange: [0, 1], outputRange: [hiddenOffset, 0] });
  const backdropOpacity = progress.interpolate({ inputRange: [0, 1], outputRange: [0, 1] });

  return (
    <Modal
      transparent
      visible
      animationType="none"
      statusBarTranslucent={Platform.OS === 'android'}
      onRequestClose={() => startExit('hardware_back')}
    >
      <View style={styles.fill}>
        <Pressable
          testID="pitaco-bottom-sheet-backdrop"
          accessibilityRole="button"
          accessibilityLabel={strings.closeA11yLabel}
          style={StyleSheet.absoluteFill}
          onPress={() => startExit('backdrop')}
        >
          <Animated.View
            style={[styles.backdrop, { backgroundColor: theme.tokens.colors.overlay, opacity: backdropOpacity }]}
          />
        </Pressable>
        {/* Teclado: `padding` no iOS e `height` no Android (ver `keyboard.ts`). */}
        <KeyboardAvoidingView
          testID="pitaco-bottom-sheet-container"
          behavior={keyboardAvoidingBehavior()}
          style={styles.keyboardAvoider}
          pointerEvents="box-none"
        >
          <Animated.View
            testID="pitaco-bottom-sheet"
            accessibilityViewIsModal
            style={[
              styles.sheet,
              {
                backgroundColor: theme.tokens.colors.background,
                borderTopLeftRadius: theme.tokens.radius.lg,
                borderTopRightRadius: theme.tokens.radius.lg,
                maxHeight: windowHeight * MAX_SHEET_HEIGHT_RATIO,
                paddingBottom: insets.bottom,
                transform: [{ translateY }],
              },
            ]}
          >
            <View
              testID="pitaco-bottom-sheet-handle"
              {...panResponder.panHandlers}
              accessibilityRole="adjustable"
              accessibilityLabel={strings.closeA11yLabel}
              accessibilityActions={[{ name: 'escape' }, { name: 'magicTap' }]}
              onAccessibilityAction={(event) => {
                if (event.nativeEvent.actionName === 'escape' || event.nativeEvent.actionName === 'magicTap') {
                  startExit('swipe');
                }
              }}
              onAccessibilityEscape={Platform.OS === 'ios' ? () => startExit('swipe') : undefined}
              style={styles.handleArea}
            >
              <View style={[styles.handle, { backgroundColor: theme.tokens.colors.border }]} />
            </View>
            <ScrollView
              style={styles.scroll}
              contentContainerStyle={styles.scrollContent}
              keyboardShouldPersistTaps="handled"
              bounces={false}
            >
              {children}
            </ScrollView>
          </Animated.View>
        </KeyboardAvoidingView>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  fill: {
    flex: 1,
  },
  backdrop: {
    flex: 1,
  },
  // `flex: 1` é o que ancora a folha embaixo: sem ele, o contêiner tem só a altura do conteúdo e
  // a folha aparece no topo da tela.
  keyboardAvoider: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  sheet: {
    width: '100%',
  },
  handleArea: {
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  handle: {
    width: 36,
    height: 4,
    borderRadius: 2,
  },
  scroll: {
    flexGrow: 0,
  },
  scrollContent: {
    flexGrow: 1,
  },
});
