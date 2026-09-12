import {
  isSuppressionReason,
  type SdkErrorKind,
  type SuppressionReason,
  type SurveyHealth,
} from "../schemas/health";

const percent = new Intl.NumberFormat("pt-BR", { style: "percent", maximumFractionDigits: 1 });
const integer = new Intl.NumberFormat("pt-BR");

export function formatShare(share: number): string {
  return percent.format(share);
}

export function formatCount(count: number): string {
  return integer.format(count);
}

export const SDK_ERROR_KIND_LABELS: Record<SdkErrorKind, string> = {
  render_error: "Renderização",
  network_error: "Rede",
  malformed_response: "Resposta malformada",
  storage_error: "Armazenamento",
  unknown: "Desconhecido",
};

const SUPPRESSION_REASON_TEXT: Record<SuppressionReason, string> = {
  unknown_question_type: "o SDK do app não conhece um tipo de pergunta usado nela",
  unsupported_feature: "o SDK do app não suporta um recurso usado nela",
};

export const STALE_NOTE =
  "Versões marcadas como “sumiu do tráfego” não consultam a elegibilidade há mais de 14 dias. " +
  "A proporção é a das consultas recentes; o total conta desde a primeira vez que a versão apareceu.";

export const SDK_ERRORS_NOTE =
  "Falhas do próprio SDK, reportadas pelo canal do Pitaco. O relatório não carrega dado do " +
  "usuário nem conteúdo de resposta, e o que parece dado pessoal é descartado no servidor.";

export type HealthNoticeKind = "suppression" | "event_missing";

export type HealthNotice = { kind: HealthNoticeKind; title: string; message: string };

function suppressionReasonText(health: SurveyHealth): string {
  const dominant = [...health.suppressions.byReason].sort((a, b) => b.count - a.count)[0];

  return dominant !== undefined && dominant.count > 0 && isSuppressionReason(dominant.reason)
    ? SUPPRESSION_REASON_TEXT[dominant.reason]
    : "o SDK do app não sabe renderizá-la";
}

/**
 * As duas causas de zero respostas que pedem correções opostas, cada uma com o seu aviso. A
 * supressão nunca é apresentada como "sem respostas": quem não viu a pesquisa não teve como
 * responder.
 */
export function surveyHealthNotices(health: SurveyHealth): HealthNotice[] {
  const notices: HealthNotice[] = [];

  if (health.relevant) {
    const total = health.suppressions.total;
    const share =
      health.suppressionShare === undefined
        ? ""
        : ` em ${formatShare(health.suppressionShare)} das vezes em que seria exibida`;
    const minimum =
      health.minRequiredVersion === undefined
        ? ""
        : ` Ela funcionaria a partir da versão ${health.minRequiredVersion} do SDK.`;

    notices.push({
      kind: "suppression",
      title: "A pesquisa está sendo suprimida",
      message:
        `O app deixou de mostrar esta pesquisa${share} (${formatCount(total)} ` +
        `${total === 1 ? "supressão" : "supressões"}), porque ${suppressionReasonText(health)}.` +
        `${minimum} Não é falta de interesse: quem não viu a pesquisa não teve como responder.`,
    });
  }

  if (health.eventName !== undefined && health.eventLastSeenAt === undefined) {
    notices.push({
      kind: "event_missing",
      title: "O evento do disparo nunca chegou",
      message:
        `O evento "${health.eventName}" nunca foi recebido desta aplicação. Sem ele, a ` +
        "pesquisa não é oferecida a ninguém: confira o nome do evento no disparo e a " +
        "integração do app.",
    });
  }

  return notices;
}

export function formatContextValue(value: unknown): string {
  return typeof value === "object" && value !== null ? JSON.stringify(value) : String(value);
}
