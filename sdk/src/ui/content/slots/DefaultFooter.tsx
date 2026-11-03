import type { ReactNode } from 'react';
import { View } from 'react-native';
import { PitacoButton } from '../../primitives/PitacoButton';
import type { FooterSlotProps } from '../../types';

// Rodapé padrão: Voltar (some quando não há como voltar) e Próxima/Enviar. `onNext` já resolve
// para conclusão sozinho na última pergunta aplicável (regra do core); o rótulo só acompanha.
//
// O botão de avançar não é desabilitado por `canGoNext`: quem valida e bloqueia é o core
// (`validation_blocked`, com `error` preenchido), não a UI. Um botão desabilitado impediria a
// pessoa de sequer tentar e o core de emitir o evento — a mensagem de erro (mostrada por
// `<PitacoSurveyContent />` a partir de `error`) é o retorno visível desse bloqueio.
export function DefaultFooter(props: FooterSlotProps): ReactNode {
  const { canGoBack, isLast, onBack, onNext, theme, strings } = props;
  return (
    <View
      style={{
        flexDirection: 'row',
        justifyContent: canGoBack ? 'space-between' : 'flex-end',
        alignItems: 'center',
        gap: theme.tokens.spacing.sm,
        paddingHorizontal: theme.tokens.spacing.lg,
        paddingVertical: theme.tokens.spacing.md,
      }}
    >
      {canGoBack ? <PitacoButton label={strings.back} variant="secondary" onPress={onBack} /> : null}
      <PitacoButton label={isLast ? strings.submit : strings.next} variant="primary" onPress={onNext} />
    </View>
  );
}
