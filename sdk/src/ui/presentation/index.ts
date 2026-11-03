// Apresentações da UI padrão (spec 002): bottom sheet e modal em tela cheia, ambos só com o que
// o React Native traz (Princípio I). `'inline'` não tem componente aqui — o SDK não abre
// contêiner nenhum nesse modo; ver `src/react/SurfaceHost.tsx`, que escolhe entre os dois e lê a
// safe area sem dependência (`resolveInsets`).
//
// Não fazem parte da API pública do SDK (não exportados por `src/index.ts`): são detalhe de
// implementação do `SurfaceHost`.

export { BottomSheet, type BottomSheetProps } from './BottomSheet';
export { FullScreenModal, type FullScreenModalProps } from './FullScreenModal';
export { platformDefaultInsets, resolveInsets } from './insets';
export { useReduceMotion } from './useReduceMotion';
