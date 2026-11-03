// `AccessibilityInfo.isReduceMotionEnabled()` + o evento `reduceMotionChanged`, para as
// apresentações cortarem a animação de entrada/saída quando a pessoa pediu movimento reduzido no
// sistema. Começa em `false` (o valor chega assíncrono) e nunca derruba a apresentação: uma API
// indisponível no ambiente (por exemplo um teste sem o módulo nativo) cai em `false` em silêncio.
import { useEffect, useState } from 'react';
import { AccessibilityInfo } from 'react-native';

export function useReduceMotion(): boolean {
  const [reduceMotion, setReduceMotion] = useState(false);

  useEffect(() => {
    let cancelled = false;

    AccessibilityInfo.isReduceMotionEnabled()
      .then((value) => {
        if (!cancelled) setReduceMotion(value);
      })
      .catch(() => undefined);

    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', (value: boolean) => {
      setReduceMotion(value);
    });

    return () => {
      cancelled = true;
      subscription.remove();
    };
  }, []);

  return reduceMotion;
}
