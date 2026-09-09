import { notFound } from "next/navigation";

import {
  DisplayAnswers,
  DisplayAttributes,
  DisplaySummaryCard,
  getDisplay,
  matchAnswersToQuestions,
} from "@/features/collect";
import { getVersion } from "@/features/surveys";
import { ApiUnavailableError } from "@/shared/api";
import { TIMEZONE_NOTE } from "@/shared/lib";

export const metadata = { title: "Exibição" };

/**
 * Duas leituras, e só duas (SC-009): a exibição é o conteúdo; a versão dá enunciado a cada
 * resposta (R2). A rota é plana sob a aplicação porque a exibição é alcançada de dois eixos (R5).
 *
 * **Em sequência, não em paralelo** — o plano previa paralelo, mas é impossível: `getVersion`
 * precisa do `surveyId` e do `versionNumber`, e os dois só existem depois que a exibição chega.
 * A contagem de leituras da tela continua sendo 2, que é o que SC-009 verifica.
 *
 * A falha na leitura da **versão** não derruba a tela: sem ela as respostas seguem visíveis
 * pela chave, com aviso. Só a falha na leitura da exibição — que é o conteúdo — sobe.
 */
export default async function DisplayPage({
  params,
}: PageProps<"/aplicacoes/[applicationId]/exibicoes/[displayId]">) {
  const { applicationId, displayId } = await params;

  const displayResult = await getDisplay(applicationId, displayId);

  if (!displayResult.ok) {
    // Exibição de outra aplicação e exibição inexistente levam à mesma tela: o painel não
    // confirma existência fora do escopo da aplicação.
    if (displayResult.kind === "not_found") {
      notFound();
    }
    throw new ApiUnavailableError(displayResult);
  }

  const display = displayResult.data;
  const versionResult = await getVersion(applicationId, display.surveyId, display.versionNumber);
  const version = versionResult.ok ? versionResult.data : undefined;

  return (
    <section data-testid="display-detail" className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 className="font-heading text-lg font-medium">Exibição</h2>
        <p data-testid="timezone-note" className="text-sm text-muted-foreground">
          {TIMEZONE_NOTE}
        </p>
      </div>

      <DisplaySummaryCard applicationId={applicationId} display={display} />

      <DisplayAnswers
        answers={matchAnswersToQuestions(display.answers, version?.questions)}
        outcome={display.outcome}
        versionAvailable={version !== undefined}
      />

      <DisplayAttributes attributes={display.attributes} />
    </section>
  );
}
