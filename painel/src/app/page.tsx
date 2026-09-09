import { redirect } from "next/navigation";

/** A raiz do painel é a listagem de aplicações — tudo mais é escopado por uma delas. */
export default function Home() {
  redirect("/aplicacoes");
}
