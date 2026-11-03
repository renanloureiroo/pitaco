import { formatDate } from "@/shared/lib";
import type { ResultQuestionType } from "../schemas/results";

const percent = new Intl.NumberFormat("pt-BR", { style: "percent", maximumFractionDigits: 1 });
const decimal = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 });
const integer = new Intl.NumberFormat("pt-BR");

/** Proporção 0..1 como percentual. Sempre acompanhada da contagem absoluta na tela. */
export function formatShare(share: number): string {
  return percent.format(share);
}

/** Taxa ausente é "sem exibição", nunca 0%. */
export function formatRate(rate: number | undefined): string {
  return rate === undefined ? "—" : percent.format(rate);
}

export function formatAverage(value: number): string {
  return decimal.format(value);
}

export function formatScore(score: number): string {
  return decimal.format(score);
}

export function formatCount(count: number): string {
  return integer.format(count);
}

export const RESULT_TYPE_LABELS: Record<ResultQuestionType, string> = {
  single_choice: "Escolha única",
  multiple_choice: "Múltipla escolha",
  rating: "Avaliação",
  scale: "Escala",
  nps: "NPS",
  free_text: "Texto livre",
};

export const NO_ANSWERS = "Ninguém respondeu esta pergunta ainda.";
export const SMALL_SAMPLE_NOTE =
  "Amostra pequena: as proporções abaixo vêm de poucas respostas e podem enganar.";
export const NO_DISPLAYS_TITLE = "Nenhuma exibição ainda";
export const NO_DISPLAYS_DESCRIPTION =
  "A pesquisa está publicada, mas ninguém disparou o evento configurado no disparo. Quando o app enviar o evento, os números aparecem aqui.";
export const NEVER_PUBLISHED_TITLE = "Esta pesquisa ainda não foi publicada";
export const NEVER_PUBLISHED_DESCRIPTION =
  "Só uma versão publicada pode ser exibida e, portanto, respondida.";
export const NO_MATCHES_TITLE = "Nenhuma exibição neste recorte";
export const NO_MATCHES_DESCRIPTION =
  "A pesquisa tem exibições, mas nenhuma atende ao recorte aplicado.";
export const EXPORT_NOTICE =
  "Respostas abertas podem conter dado pessoal que alguém escreveu por conta própria. Revise o arquivo antes de compartilhá-lo.";
export const OPEN_ANSWERS_NOTICE =
  "Texto livre pode conter dado pessoal escrito pelo respondente. Trate esta lista com o mesmo cuidado de um export.";
export const MULTIPLE_CHOICE_SHARE_NOTE =
  "Em múltipla escolha cada opção conta quem a marcou, então as proporções somam mais de 100%.";

export const NOT_APPLICABLE_NOTE =
  "Não aplicável é quem nunca viu a pergunta, porque a condição de exibição a pulou. Fica fora " +
  "das proporções, e é diferente de pulada.";

/** "1 e 2", "1, 2 e 3": as versões como se lê numa frase. */
export function formatVersionList(versions: number[]): string {
  const sorted = [...versions].sort((a, b) => a - b).map(String);
  return sorted.length <= 1
    ? (sorted[0] ?? "")
    : `${sorted.slice(0, -1).join(", ")} e ${sorted[sorted.length - 1]}`;
}

export function incomparableMessage(versions: number[]): string {
  return (
    `Esta pergunta mudou entre as versões ${formatVersionList(versions)}; somar as respostas ` +
    "pode enganar. Escolha uma versão na visão para ler cada uma separada."
  );
}

export function comparableMessage(versions: number[]): string {
  return `Sem mudança de sentido entre as versões ${formatVersionList(versions)}: é seguro somar.`;
}

export type ResultsEmptyVariant = "never_published" | "no_displays" | "no_matches";

export function resultsEmptyVariant({
  everPublished,
  displayed,
  filtered,
}: {
  everPublished: boolean;
  displayed: number;
  filtered: boolean;
}): ResultsEmptyVariant | undefined {
  if (!everPublished) {
    return "never_published";
  }
  if (displayed > 0) {
    return undefined;
  }
  return filtered ? "no_matches" : "no_displays";
}

/** Datas no fuso de referência do painel; o instante exato fica no backend. */
export function retentionNoteText(retention: { snapshotApplied: boolean; discardedBefore: string }): string {
  const date = formatDate(retention.discardedBefore);
  return retention.snapshotApplied
    ? `Inclui agregados de respostas descartadas pela política de retenção, dadas antes de ${date}. As respostas em si não aparecem nas respostas abertas nem no export.`
    : `Respostas dadas antes de ${date}, descartadas pela política de retenção, não entram neste recorte: o agregado guardado não tem data nem atributo.`;
}
