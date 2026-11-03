import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

import type { VersionComparability } from "../../schemas/version";

/** Versões dentro do mesmo grupo têm respostas somáveis entre si. */
export function ComparabilityPanel({
  comparability,
}: {
  comparability: VersionComparability;
}) {
  return (
    <Card data-testid="comparability-panel">
      <CardHeader>
        <CardTitle>Comparabilidade</CardTitle>
      </CardHeader>
      <CardContent>
        {comparability.groups.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            Ainda não há versão publicada para comparar.
          </p>
        ) : (
          <ul className="flex flex-col gap-2">
            {comparability.groups.map((group) => (
              <li key={group.group} className="flex flex-wrap items-center gap-2 text-sm">
                <span className="text-muted-foreground">Grupo {group.group}:</span>
                <span>
                  {group.versions.map((version) => `v${version}`).join(", ")}
                </span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
