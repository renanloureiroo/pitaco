import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

import type { FreeTextNotice } from "../../schemas/survey";
import { FreeTextNoticeForm } from "./free-text-notice-form";

/**
 * O aviso de dado pessoal no texto livre. Aparece sempre, com a explicação, porque a pergunta de
 * texto livre pode chegar numa versão seguinte — e o aviso já estará configurado.
 */
export function FreeTextNoticePanel({
  applicationId,
  surveyId,
  notice,
  hasFreeText,
  readOnly = false,
}: {
  applicationId: string;
  surveyId: string;
  notice: FreeTextNotice | undefined;
  hasFreeText: boolean;
  readOnly?: boolean;
}) {
  if (notice === undefined) {
    return null;
  }

  return (
    <Card data-testid="free-text-notice-panel">
      <CardHeader>
        <CardTitle>Aviso de dado pessoal</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <p data-testid="free-text-notice-current" className="text-sm">
          {notice.enabled ? `Exibido: "${notice.text}"` : "Desligado: os campos de texto livre aparecem sem aviso."}
        </p>
        {hasFreeText ? null : (
          <p data-testid="free-text-notice-unused" className="text-sm text-muted-foreground">
            Esta pesquisa ainda não tem pergunta de texto livre: o aviso só aparece quando houver
            uma.
          </p>
        )}
        {readOnly ? null : (
          <FreeTextNoticeForm applicationId={applicationId} surveyId={surveyId} notice={notice} />
        )}
      </CardContent>
    </Card>
  );
}
