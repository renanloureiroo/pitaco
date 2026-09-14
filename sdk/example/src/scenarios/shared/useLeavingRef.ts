// `true` a partir do instante em que a rota começa a sair da pilha, por qualquer caminho: a seta do
// cabeçalho, o gesto de voltar do iOS, o voltar do Android ou um `router.back()` nosso. As rotas
// de pesquisa (cenários 3 e 4) usam isto no `onFinish` do conteúdo: ao desmontar em andamento, o
// `<PitacoSurveyContent />` dispensa com `via: "navigation"` e chama `onFinish('dismissed')`; a
// rota já está saindo e não pode voltar de novo.
import { useNavigation } from 'expo-router';
import { useEffect, useRef } from 'react';

export function useLeavingRef() {
  const navigation = useNavigation();
  const leavingRef = useRef(false);

  useEffect(
    () =>
      navigation.addListener('beforeRemove', () => {
        leavingRef.current = true;
      }),
    [navigation],
  );

  return leavingRef;
}
