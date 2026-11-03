// Seletor do perfil de conexão (direto ou pelo proxy). Trocar o perfil troca o `baseUrl` do
// `<PitacoProvider>` da raiz, e o runtime é recriado.
import { StyleSheet, Text, View } from 'react-native';
import { Segmented } from '../ui/Segmented';
import { usePalette } from '../ui/palette';
import type { ConnectionProfileId } from './config';
import { useExampleActions, useExampleState } from './ExampleContext';

export function ProfileSelector() {
  const palette = usePalette();
  const { profile, profiles } = useExampleState();
  const { setProfileId } = useExampleActions();

  return (
    <View style={styles.container}>
      <Segmented<ConnectionProfileId>
        testIDPrefix="perfil"
        value={profile.id}
        onChange={setProfileId}
        options={profiles.map((item) => ({ value: item.id, label: item.label }))}
      />
      <Text testID="perfil-endereco" style={[styles.detail, { color: profile.available ? palette.muted : palette.danger }]}>
        {profile.available
          ? profile.baseUrl
          : `Perfil sem configuração: defina ${profile.description.split(' ')[0]} e EXPO_PUBLIC_PITACO_API_KEY no .env (npm run seed).`}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 6 },
  detail: { fontSize: 12 },
});
