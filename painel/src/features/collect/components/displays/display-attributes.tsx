import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

/**
 * O instantâneo de atributos pertence à **exibição**, não ao respondente (FR-014).
 *
 * A tela precisa dizer isso em texto: o mesmo respondente pode ter instantâneos diferentes em
 * exibições diferentes, e lê-lo como perfil seria erro de interpretação, não de layout.
 */
export function DisplayAttributes({ attributes }: { attributes: Record<string, string> }) {
  const entries = Object.entries(attributes);

  return (
    <Card data-testid="display-attributes">
      <CardHeader>
        <CardTitle>Atributos desta exibição</CardTitle>
        <p className="text-sm text-muted-foreground">
          O que a aplicação informou no momento da elegibilidade desta exibição. Não é o perfil
          do respondente: outra exibição pode ter outros valores.
        </p>
      </CardHeader>
      <CardContent>
        {entries.length === 0 ? (
          <p data-testid="attributes-empty" className="text-sm text-muted-foreground">
            Nenhum atributo informado nesta exibição.
          </p>
        ) : (
          <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {entries.map(([name, value]) => (
              <div key={name} data-testid="attribute-item" className="flex flex-col gap-1">
                <dt className="font-mono text-xs text-muted-foreground">{name}</dt>
                <dd className="text-sm">{value}</dd>
              </div>
            ))}
          </dl>
        )}
      </CardContent>
    </Card>
  );
}
