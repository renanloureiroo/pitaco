import { formatDate } from "@/shared/lib";

import type { RespondentErasure, RetentionPreview } from "../schemas/privacy";

export const ERASURE_TITLE = "Excluir este respondente?";

export function erasureDescription(identity: string): string {
  return `Todas as exibições e respostas de "${identity}" nesta aplicação serão apagadas, em todas as pesquisas. A exclusão é irreversível: não há lixeira nem como desfazer. Fica registrado apenas que houve uma exclusão, quando e quanto saiu — nunca quem.`;
}

export const AUDIT_NOTE =
  "Cada exclusão registra quando aconteceu e quanto saiu. Quem foi excluído não fica guardado em lugar nenhum.";

function plural(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`;
}

export function erasureResultMessage(result: RespondentErasure): string {
  if (!result.deleted) {
    return "Nenhum respondente com essa identificação nesta aplicação. Nada foi apagado.";
  }
  return `Excluído: ${plural(result.displaysDeleted, "exibição", "exibições")} e ${plural(result.answersDeleted, "resposta", "respostas")} apagadas.`;
}

function days(count: number): string {
  return plural(count, "dia", "dias");
}

export function describeRetentionPolicy(preview: RetentionPreview): string {
  if (!preview.configured) {
    return "Sem política de retenção: nada é descartado automaticamente.";
  }

  const parts: string[] = [];
  if (preview.answerRetentionDays !== undefined) {
    parts.push(`As respostas são apagadas ${days(preview.answerRetentionDays)} depois de dadas.`);
  }
  if (
    preview.textRetentionDays !== undefined &&
    preview.textRetentionDays !== preview.answerRetentionDays
  ) {
    parts.push(`O texto livre some ${days(preview.textRetentionDays)} depois.`);
  }
  parts.push("O agregado de cada pesquisa continua nos resultados depois do descarte.");
  return parts.join(" ");
}

function discarded(answers: number, texts: number): string {
  return `${plural(answers, "resposta", "respostas")} e ${plural(texts, "texto livre", "textos livres")}`;
}

/**
 * O aviso antes do descarte, com o prazo para exportar. Ausente quando não há política, ou
 * quando nada vence nem na próxima semana.
 */
export function retentionWarning(preview: RetentionPreview): string | undefined {
  if (!preview.configured) {
    return undefined;
  }
  if (preview.nextRunAt === undefined) {
    return "O descarte automático está desligado nesta instância: nada sai até ele ser ligado.";
  }

  const { nextRun, nextWeek } = preview;
  if (nextRun.answers + nextRun.texts > 0) {
    return `Na próxima execução, em ${formatDate(preview.nextRunAt)}, ${discarded(nextRun.answers, nextRun.texts)} serão descartados. Exporte antes se precisar.`;
  }
  if (nextWeek.answers + nextWeek.texts > 0) {
    const weekLater = new Date(new Date(preview.nextRunAt).getTime() + 7 * 24 * 60 * 60 * 1000);
    return `Até ${formatDate(weekLater.toISOString())}, ${discarded(nextWeek.answers, nextWeek.texts)} terão sido descartados. Exporte antes se precisar.`;
  }
  return undefined;
}

export function lastRunMessage(preview: RetentionPreview): string {
  return preview.lastRunAt === undefined
    ? "Nenhum descarte aconteceu ainda nesta aplicação."
    : `Último descarte em ${formatDate(preview.lastRunAt)}.`;
}
