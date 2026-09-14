// Renderizador de avaliação que lança erro de propósito. O SDK o isola num error boundary: a
// pergunta cai nas estrelas padrão, avisa uma vez em desenvolvimento e manda o relatório de erro
// (`POST /collect/sdk-errors`, `kind: "render_error"`), que aparece na aba de rede do painel.
import type { QuestionRendererProps } from '@pitaco/react-native';
import { LogBox } from 'react-native';

// O erro é de propósito e o SDK já o trata. Sem isto, o LogBox de desenvolvimento cobre a tela e
// esconde justamente o que o cenário mostra (as estrelas padrão no lugar). Os outros dois são os
// avisos de desenvolvimento que o SDK escreve ao cair no padrão e ao relatar a falha: no Android, a
// notificação do LogBox fica sobre o "Próxima" da folha e engole o toque. Os três continuam no
// console do Metro. Não muda nada em produção.
LogBox.ignoreLogs([
  'Renderizador de avaliação quebrado de propósito',
  'o renderizador substituído "rating" lançou um erro',
  'falha interna (render_error)',
]);

export function BrokenRating(props: QuestionRendererProps): never {
  throw new Error(`Renderizador de avaliação quebrado de propósito (pergunta ${props.question.position}).`);
}
