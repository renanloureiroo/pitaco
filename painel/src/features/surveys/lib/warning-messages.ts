import type { PublicationWarning } from "../schemas/warnings";

const percent = new Intl.NumberFormat("pt-BR", { style: "percent", maximumFractionDigits: 0 });

/** A regra de desempate, dita do mesmo jeito em todo lugar em que ela aparece. */
export const TIEBREAK_RULE =
  "Quando mais de uma pesquisa disputa o mesmo evento, vence a de maior prioridade; em empate, a publicada há mais tempo.";

export function warningMessage(warning: PublicationWarning): string {
  switch (warning.code) {
    case "trigger.competing_surveys":
      return "Outras pesquisas no ar escutam o mesmo evento. Só uma é entregue por vez.";
    case "segmentation.no_known_match":
      return `A regra sobre "${warning.attribute ?? ""}" exige um atributo ou valor que o app nunca enviou: hoje ela não alcança ninguém.`;
    case "segmentation.contradictory":
      return `As regras sobre "${warning.attribute ?? ""}" se contradizem: nenhum respondente satisfaz todas ao mesmo tempo.`;
    case "compatibility.unsupported_by_majority": {
      const version = warning.minRequiredVersion ?? "mais recente";
      const share =
        warning.unsupportedShare === undefined
          ? "a maior parte"
          : percent.format(warning.unsupportedShare);
      return `Esta pesquisa exige o SDK ${version} ou mais novo, e ${share} do tráfego recente vem de versões anteriores: para essas pessoas, ela não vai aparecer.`;
    }
    default:
      return "Há um ponto de atenção nesta pesquisa.";
  }
}
