// A UI inteiramente própria do cenário 9, só sobre `usePitacoSurvey()`: o app desenha cartão,
// progresso, pergunta, erro e botões, e cada toque é uma ação do core. O tracking é o mesmo da UI
// padrão, porque quem emite os eventos é o core.
// - Ao montar (o cartão ficou visível), `present()` abre a exibição.
// - Avançar e Concluir chamam `next()`: na última pergunta aplicável o core conclui sozinho, como no
//   rodapé padrão (a mesma sequência de eventos, com `question_skipped` quando for o caso).
// - Pular é `next()` numa opcional em branco; Dispensar é `dismiss('close_button')`.
// - Sair da tela com a pesquisa em andamento (seta, gesto do iOS, voltar do Android) é
//   `dismiss('navigation')` ao desmontar, o que o `<PitacoSurveyContent />` faz sozinho e uma UI
//   própria precisa fazer à mão. Sem isto a exibição fica sem desfecho (vira abandono no servidor).
import { usePitacoSurvey } from '@pitaco/react-native';
import { useEffect, useRef } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { ActionButton } from '../../ui/ActionButton';
import { usePalette } from '../../ui/palette';
import { HeadlessQuestion } from './HeadlessQuestion';

function isBlank(value: unknown): boolean {
  return value === undefined || value === '' || (Array.isArray(value) && value.length === 0);
}

export function HeadlessSurvey({ onDone }: { onDone: () => void }) {
  const survey = usePitacoSurvey();
  const palette = usePalette();
  const { present, status } = survey;

  useEffect(() => {
    present('inline');
  }, [present]);

  useEffect(() => {
    if (status === 'dismissed') onDone();
  }, [status, onDone]);

  // O valor mais recente para a limpeza de desmontagem, atualizado depois de cada render.
  const surveyRef = useRef(survey);
  useEffect(() => {
    surveyRef.current = survey;
  });

  useEffect(() => {
    return () => {
      const current = surveyRef.current;
      if (current.status === 'ready' || current.status === 'presented') current.dismiss('navigation');
    };
  }, []);

  const card = [styles.card, { backgroundColor: palette.surface, borderColor: palette.border }];

  if (status === 'completed') {
    return (
      <View testID="cenario-09-obrigado" style={card}>
        <Text style={[styles.title, { color: palette.text }]}>Valeu! Resposta enviada pela UI própria.</Text>
        <ActionButton testID="cenario-09-fechar" label="Fechar" variant="secondary" onPress={onDone} />
      </View>
    );
  }

  const question = survey.question;
  if (question === null) return null;

  return (
    <View testID="cenario-09-pesquisa" style={card}>
      <View style={styles.header}>
        <Text style={[styles.progress, { color: palette.muted }]}>
          {survey.progress.position}/{survey.progress.total}
        </Text>
        <ActionButton testID="cenario-09-dispensar" label="Dispensar" variant="danger" onPress={() => survey.dismiss('close_button')} />
      </View>
      <Text testID="cenario-09-titulo" style={[styles.title, { color: palette.text }]}>
        {question.statement}
        {question.required ? ' *' : ''}
      </Text>
      {survey.error !== null && (
        <Text testID="cenario-09-erro" style={{ color: palette.danger }}>
          Responda esta para continuar.
        </Text>
      )}
      <HeadlessQuestion survey={survey} />
      <View style={styles.nav}>
        {survey.canGoBack && <ActionButton testID="cenario-09-voltar" label="Voltar" variant="secondary" onPress={() => survey.back()} />}
        {!question.required && isBlank(survey.value) && (
          <ActionButton testID="cenario-09-pular" label="Pular" variant="secondary" onPress={() => survey.next()} />
        )}
        <ActionButton
          testID={survey.isLast ? 'cenario-09-concluir' : 'cenario-09-avancar'}
          label={survey.isLast ? 'Concluir' : 'Avançar'}
          onPress={() => survey.next()}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: { borderWidth: 1, borderRadius: 14, padding: 14, gap: 12 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  progress: { fontSize: 13, fontWeight: '700' },
  title: { fontSize: 17, fontWeight: '700', lineHeight: 23 },
  nav: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, justifyContent: 'flex-end' },
});
