import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useExampleActions } from '../pitaco/ExampleContext';
import { usePalette } from './palette';

export function FloatingResetButton() {
  const { requestFreshInstall } = useExampleActions();
  const palette = usePalette();
  const [resetting, setResetting] = useState(false);

  const handlePress = async () => {
    setResetting(true);
    await requestFreshInstall();
    setResetting(false);
  };

  return (
    <View style={styles.container} pointerEvents="box-none">
      <Pressable
        onPress={handlePress}
        disabled={resetting}
        style={({ pressed }) => [
          styles.button,
          { backgroundColor: palette.danger },
          pressed && styles.pressed,
          resetting && styles.disabled,
        ]}
      >
        <Text style={styles.text}>{resetting ? 'Resetando...' : 'Resetar Estado'}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    bottom: 90,
    right: 20,
    zIndex: 9999,
  },
  button: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 24,
    elevation: 5,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
  },
  pressed: {
    opacity: 0.8,
  },
  disabled: {
    opacity: 0.5,
  },
  text: {
    color: '#FFF',
    fontWeight: 'bold',
    fontSize: 14,
  },
});
