---
tipo: adr
numero: NNNN
status: proposto # proposto | aceito | superado | revogado
data: AAAA-MM-DD
supera: # ADR que este substitui, se houver
superado_por: # preenchido quando outro ADR substituir este
tags:
  - pitaco
  - adr
---

# ADR-NNNN — Título na forma de decisão

> Uma frase que diga a decisão inteira. Quem ler só isto já sabe o que foi decidido.

## Contexto

O que era verdade quando a decisão foi tomada: a restrição, o problema, o que já
existia no código. Fatos, não justificativa — a justificativa vem depois.

Escreva no passado e datado. Um ADR não é atualizado quando o mundo muda; ele é
superado por outro. O valor dele é registrar o que se sabia **naquele dia**.

## Alternativas consideradas

Cada opção que esteve genuinamente em jogo, com o que ela tinha de bom. Uma
alternativa descrita só pelos defeitos é sinal de que ela nunca foi considerada
de verdade — e um ADR assim não convence ninguém, inclusive você daqui a um ano.

### Opção A

### Opção B

## Decisão

O que foi escolhido, e a razão que efetivamente decidiu — não a lista de todas as
razões plausíveis. Se a razão foi contextual (prazo, aprendizado, o time que
existe), diga isso. Motivo honesto envelhece melhor que motivo nobre inventado.

## Consequências

O que passa a ser verdade por causa da decisão.

**Aceitamos:** o que fica pior, e que se sabia que ficaria pior. Um ADR sem esta
parte está escondendo o trade-off.

**Ganhamos:** o que fica melhor.

**Muda no repositório:** o que precisa ser feito ou refeito por causa disto —
documento superado, código a migrar, convenção a adotar.

## Quando revisitar

O sinal concreto que faria valer a pena reabrir a decisão. Não "se as coisas
mudarem", mas algo que dê para observar acontecendo.
