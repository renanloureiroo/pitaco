// `<PitacoSurveyContent />`: a pesquisa ativa, sem contêiner nenhum — cabeçalho, progresso,
// pergunta atual pelo registro de renderizadores, rodapé com Voltar/Próxima/Enviar, botão de
// fechar em toda pergunta e a tela de agradecimento. Construído só sobre `usePitacoSurvey()` e a
// API pública: nenhuma ação privilegiada, o mesmo que um app faria na própria UI headless.
//
// Quem apresenta (bottom sheet, modal ou uma tela de navegação) entra por fora, deste componente
// para dentro; ele nunca decide como é mostrado.

import { useContext, useEffect, useMemo, useRef, type ComponentRef, type ReactNode } from 'react';
import { AccessibilityInfo, findNodeHandle, Platform, StyleSheet, Text, View } from 'react-native';
import type { DismissVia } from '../../catalog/events';
import { PitacoContext } from '../../react/context';
import { usePitacoSurvey } from '../../react/usePitacoSurvey';
import { DEFAULT_MAX_FONT_SIZE_MULTIPLIER, PitacoText } from '../primitives/PitacoText';
import { DEFAULT_RENDERERS } from '../questions';
import { SafeCustom } from '../registry/SafeCustom';
import { moveAccessibilityFocus } from './focus';
import { DEFAULT_SLOTS } from './slots';
import { usePitacoStrings } from '../strings/useStrings';
import { usePitacoTheme } from '../theme/useTheme';
import {
  rendererKeyForQuestionType,
  type PitacoRendererMap,
  type PitacoSlotMap,
  type QuestionRendererActions,
  type QuestionRendererProps,
} from '../types';

export type PitacoSurveyFinishReason = 'completed' | 'dismissed';

const DEFAULT_THANK_YOU_DURATION_MS = 2500;

export interface PitacoSurveyContentProps {
  // Chamado ao concluir ou dispensar, para o app fechar o próprio contêiner (voltar a
  // navegação, fechar o sheet, etc.).
  readonly onFinish?: (reason: PitacoSurveyFinishReason) => void;
  // `true` quando o contêiner adia a chamada de `present()` para depois da própria animação de
  // entrada (o sheet só está de fato visível quando ela termina) e a chama por conta própria
  // através de `usePitacoSurvey().present()`. Padrão `false`: este componente chama ao montar.
  readonly presentDeferred?: boolean;
  // Via usada ao desmontar com a pesquisa em andamento. Padrão `'navigation'` (a pessoa saiu por
  // navegação do app, sem usar um controle do próprio Pitaco).
  readonly dismissVia?: DismissVia;
  // Duração da tela de agradecimento antes de chamar `onFinish('completed')`. Padrão 2500 ms.
  readonly thankYouDurationMs?: number;
  // Sobrepõe, tipo a tipo, os renderizadores do Provider (que por sua vez sobrepõem o padrão).
  readonly renderers?: Partial<PitacoRendererMap>;
  // Sobrepõe, slot a slot, os slots do Provider.
  readonly slots?: Partial<PitacoSlotMap>;
}

export function PitacoSurveyContent(props: PitacoSurveyContentProps): ReactNode {
  const { onFinish, presentDeferred = false, dismissVia = 'navigation', thankYouDurationMs = DEFAULT_THANK_YOU_DURATION_MS } = props;

  const survey = usePitacoSurvey();
  const context = useContext(PitacoContext);
  const theme = usePitacoTheme();
  const strings = usePitacoStrings();

  // Refs com os valores mais recentes, para os efeitos de apresentação/desmontagem (que rodam
  // uma vez) e a limpeza de desmontagem (que precisa da via e do `onFinish` do último render).
  // Atualizadas num efeito sem dependências (roda a cada render, sempre depois dele) — nunca
  // durante o próprio render.
  const surveyRef = useRef(survey);
  const onFinishRef = useRef(onFinish);
  const dismissViaRef = useRef(dismissVia);
  const finishedRef = useRef(false);

  useEffect(() => {
    surveyRef.current = survey;
    onFinishRef.current = onFinish;
    dismissViaRef.current = dismissVia;
  });

  // Ao ficar visível pela primeira vez, informa o core (`present()`), a menos que o contêiner
  // peça para adiar (ex.: o sheet ainda está animando a entrada).
  useEffect(() => {
    if (presentDeferred) return;
    surveyRef.current.present();
  }, [presentDeferred]);

  // Ao desmontar com a pesquisa em andamento (e sem um desfecho já visto), dispensa com a via
  // configurada — o caso do cenário 3 do exemplo: sair pela navegação do app.
  useEffect(() => {
    return () => {
      if (finishedRef.current) return;
      if (surveyRef.current.status === 'presented' || surveyRef.current.status === 'ready') {
        surveyRef.current.dismiss(dismissViaRef.current);
        onFinishRef.current?.('dismissed');
      }
    };
  }, []);

  // Marca o desfecho assim que o core registra um (evita a dispensa dupla acima quando o
  // desfecho já veio de outro lugar — o botão de fechar deste componente, ou uma ação externa).
  useEffect(() => {
    if (survey.status !== 'completed' && survey.status !== 'dismissed') return;
    finishedRef.current = true;
    if (survey.status === 'dismissed') onFinishRef.current?.('dismissed');
  }, [survey.status]);

  const customRenderers = useMemo(
    () => ({ ...context?.ui.renderers, ...props.renderers }),
    [context?.ui.renderers, props.renderers],
  );
  const customSlots = useMemo(() => ({ ...context?.ui.slots, ...props.slots }), [context?.ui.slots, props.slots]);

  const titleRef = useRef<ComponentRef<typeof Text>>(null);
  const questionKey = survey.question?.key ?? null;

  // Foco de acessibilidade movido para o título da pergunta nova a cada navegação.
  useEffect(() => {
    if (questionKey === null) return;
    
    if (Platform.OS === 'web') {
      const el = titleRef.current as any;
      // No web, o foco nativo resolve a acessibilidade
      if (el && typeof el.focus === 'function') {
        el.focus({ preventScroll: true });
      }
      return;
    }

    const handle = findNodeHandle(titleRef.current);
    moveAccessibilityFocus(typeof handle === 'number' ? handle : null, (focusHandle) =>
      AccessibilityInfo.setAccessibilityFocus(focusHandle),
    );
  }, [questionKey]);

  const handleClose = () => survey.dismiss('close_button');
  const handleThankYouDone = () => onFinishRef.current?.('completed');

  if (survey.status === 'idle' || survey.status === 'discarded' || survey.status === 'dismissed') {
    return null;
  }

  if (survey.status === 'completed') {
    return (
      <SafeCustom
        kind="slot"
        name="ThankYou"
        custom={customSlots.ThankYou}
        Default={DEFAULT_SLOTS.ThankYou}
        props={{ theme, strings, durationMs: thankYouDurationMs, onDone: handleThankYouDone }}
      />
    );
  }

  const question = survey.question;
  if (question === null) return null;

  const rendererKey = rendererKeyForQuestionType(question.type);
  const actions: QuestionRendererActions = {
    select: (value) => survey.select(value),
    deselect: (value) => survey.deselect(value),
    setText: (text) => survey.setText(text),
    focusText: () => survey.focusText(),
    blurText: () => survey.blurText(),
  };
  const freeTextNotice =
    question.type === 'FREE_TEXT' && survey.survey?.freeTextNotice.enabled === true
      ? (survey.survey.freeTextNotice.text ?? strings.freeTextNoticeDefault)
      : null;
  const rendererProps: QuestionRendererProps = {
    question,
    value: survey.value,
    error: survey.error,
    actions,
    theme,
    strings,
    freeTextNotice,
  };

  return (
    <View>
      <SafeCustom
        kind="slot"
        name="Header"
        custom={customSlots.Header}
        Default={DEFAULT_SLOTS.Header}
        props={{ survey: survey.survey ?? emptySurveySummary(), progress: survey.progress, theme, strings }}
      />
      <SafeCustom
        kind="slot"
        name="Progress"
        custom={customSlots.Progress}
        Default={DEFAULT_SLOTS.Progress}
        props={{ position: survey.progress.position, total: survey.progress.total, theme, strings }}
      />
      <View style={[styles.titleRow, { paddingHorizontal: theme.tokens.spacing.lg, paddingTop: theme.tokens.spacing.sm }]}>
        <Text
          ref={titleRef}
          accessible
          accessibilityRole="header"
          maxFontSizeMultiplier={DEFAULT_MAX_FONT_SIZE_MULTIPLIER}
          accessibilityLabel={strings.questionA11yLabel({
            position: survey.progress.position,
            total: survey.progress.total,
            statement: question.statement,
            required: question.required,
          })}
          style={[
            styles.title,
            {
              color: theme.tokens.colors.textPrimary,
              fontSize: theme.tokens.typography.title.fontSize,
              lineHeight: theme.tokens.typography.title.lineHeight,
            },
          ]}
        >
          {question.statement}
          {question.required ? ` (${strings.requiredBadge})` : ''}
        </Text>
        <SafeCustom
          kind="slot"
          name="CloseButton"
          custom={customSlots.CloseButton}
          Default={DEFAULT_SLOTS.CloseButton}
          props={{ onClose: handleClose, theme, strings }}
        />
      </View>
      {survey.error !== null ? (
        <PitacoText
          variant="caption"
          color={theme.tokens.colors.danger}
          style={{ paddingHorizontal: theme.tokens.spacing.lg, paddingTop: theme.tokens.spacing.xs }}
        >
          {strings.validationRequired}
        </PitacoText>
      ) : null}
      <View style={{ padding: theme.tokens.spacing.lg }}>
        <SafeCustom
          kind="renderer"
          name={rendererKey}
          custom={customRenderers[rendererKey]}
          Default={DEFAULT_RENDERERS[rendererKey]}
          props={rendererProps}
          resetKey={question.key}
        />
      </View>
      <SafeCustom
        kind="slot"
        name="Footer"
        custom={customSlots.Footer}
        Default={DEFAULT_SLOTS.Footer}
        props={{
          canGoBack: survey.canGoBack,
          canGoNext: survey.canGoNext,
          isLast: survey.isLast,
          onBack: () => survey.back(),
          onNext: () => survey.next(),
          theme,
          strings,
        }}
      />
    </View>
  );
}

function emptySurveySummary() {
  return {
    surveyId: '',
    versionId: '',
    versionNumber: 0,
    questionCount: 0,
    renderableCount: 0,
    freeTextNotice: { enabled: false, text: null },
  };
}

const styles = StyleSheet.create({
  titleRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: 8,
  },
  title: {
    flex: 1,
    fontWeight: '700',
  },
});
