import type { SurveyTemplate } from "../schemas/survey";

export type SurveyTemplateOption = {
  value: "blank" | SurveyTemplate;
  label: string;
  description: string;
};

/** As escolhas de "começar de", com o que cada modelo põe no rascunho dito em uma linha. */
export const SURVEY_TEMPLATE_OPTIONS: SurveyTemplateOption[] = [
  { value: "blank", label: "Em branco", description: "Sem perguntas. Você monta do zero." },
  {
    value: "nps",
    label: "NPS",
    description:
      "De 0 a 10, o quanto recomendaria a um amigo ou colega. O resultado calcula o NPS sozinho.",
  },
  {
    value: "csat",
    label: "CSAT",
    description: "Satisfação de 1 a 5, de muito insatisfeito a muito satisfeito.",
  },
  {
    value: "ces",
    label: "CES",
    description: "Esforço: concordância de 1 a 7 com “este app facilitou resolver o que eu precisava”.",
  },
];

export const SURVEY_TEMPLATE_LABELS: Record<SurveyTemplate, string> = {
  nps: "NPS",
  csat: "CSAT",
  ces: "CES",
};
