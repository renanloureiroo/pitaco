// Cenário 4 — Testar pesquisas. Escolhe uma pesquisa do seed, a forma e o tema, e dispara o evento
// dela pelo `<PitacoProvider>` da raiz: elegibilidade, exibição e respostas vão para o backend e
// aparecem nos resultados do painel.
//
// - Sheet do SDK: `presentation="bottom-sheet"`, o SDK abre o sheet dele.
// - Gorhom: `presentation="inline"`, o app abre o `BottomSheetModal` quando a pesquisa chega.
// - Tela: `presentation="inline"`, o app empilha `04-comparar-tela` quando a pesquisa chega.
//
// A forma é escolhida antes do disparo: trocar `presentation` recria o runtime, e um `track` no
// mesmo toque cairia no runtime antigo.
import { usePitaco } from "@pitaco/react-native";
import { useRouter } from "expo-router";
import { useEffect, useRef, useState } from "react";
import { Pressable, ScrollView, Text, View } from "react-native";
import { seedSurveys } from "../../../src/pitaco/seed";
import { useScenario } from "../../../src/pitaco/useScenario";
import { GorhomSurveySheet } from "../../../src/scenarios/02-gorhom/GorhomSurveySheet";
import {
  type CompareForm,
  compareConfig,
  FORM_OPTIONS,
} from "../../../src/scenarios/04-comparar/configs";
import {
  frameColors,
  THEME_OPTIONS,
  type ThemeChoice,
} from "../../../src/scenarios/04-comparar/themes";
import { ResetHint } from "../../../src/scenarios/shared/TriggerButton";
import { useSurveyArrival } from "../../../src/scenarios/shared/useSurveyArrival";
import { ActionButton } from "../../../src/ui/ActionButton";
import { Hint, Paragraph, Screen } from "../../../src/ui/Screen";
import { Segmented } from "../../../src/ui/Segmented";
import { usePalette } from "../../../src/ui/palette";

export default function CompareScenario() {
  const [form, setForm] = useState<CompareForm>("sheet");
  const [choice, setChoice] = useState<ThemeChoice>("claro");
  const [selectedSurveyIndex, setSelectedSurveyIndex] = useState(0);
  // A forma que disparou a exibição em curso: trocar o seletor depois não reinterpreta a chegada.
  const [launchedForm, setLaunchedForm] = useState<CompareForm | null>(null);
  useScenario("04-comparar", compareConfig(form, choice));
  const router = useRouter();
  const palette = usePalette();
  const { track, simulateAppReopen } = usePitaco();
  const { active, finish, arrivedId } = useSurveyArrival();
  const pushedRef = useRef<string | null>(null);

  const selectedSurvey = seedSurveys[selectedSurveyIndex] ?? null;
  const colors = frameColors(choice);
  const gorhomOpen = launchedForm === "gorhom" && active;

  useEffect(() => {
    if (launchedForm !== "tela" || arrivedId === null || arrivedId === pushedRef.current) return;
    pushedRef.current = arrivedId;
    router.push({ pathname: "/cenarios/04-comparar-tela", params: { tema: choice } });
  }, [launchedForm, arrivedId, choice, router]);

  const openSurvey = () => {
    if (selectedSurvey === null) return;
    finish();
    setLaunchedForm(form);
    // Uma pesquisa por sessão de app: sem isto, só a primeira abertura consultaria o servidor.
    simulateAppReopen();
    void track(selectedSurvey.triggerEvent);
  };

  return (
    <>
      <Screen testID="tela-04-comparar">
        <Paragraph>
          Escolha a pesquisa, a forma e o tema. A abertura passa pelo backend:
          a exibição e as respostas aparecem nos resultados do painel.
        </Paragraph>
        {seedSurveys.length === 0 ? (
          <Hint>
            Pesquisa do seed não encontrada: rode `npm run seed` com o backend
            no ar.
          </Hint>
        ) : (
          <>
            <View style={{ marginVertical: 8 }}>
              <Text
                style={{
                  fontSize: 14,
                  fontWeight: "600",
                  color: palette.text,
                  marginBottom: 8,
                }}
              >
                Selecione a pesquisa:
              </Text>
              <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                contentContainerStyle={{ gap: 8 }}
              >
                {seedSurveys.map((survey, index) => {
                  const isSelected = index === selectedSurveyIndex;
                  return (
                    <Pressable
                      key={survey.surveyId}
                      onPress={() => setSelectedSurveyIndex(index)}
                      style={[
                        {
                          paddingHorizontal: 12,
                          paddingVertical: 8,
                          borderRadius: 8,
                          borderWidth: 1,
                          borderColor: isSelected
                            ? palette.accent
                            : palette.border,
                          backgroundColor: isSelected
                            ? palette.accent
                            : palette.surface,
                        },
                      ]}
                    >
                      <Text
                        style={{
                          color: isSelected ? palette.onAccent : palette.text,
                          fontSize: 13,
                          fontWeight: "500",
                        }}
                      >
                        {survey.title}
                      </Text>
                    </Pressable>
                  );
                })}
              </ScrollView>
            </View>
            <Segmented
              testIDPrefix="cenario-04-forma"
              options={FORM_OPTIONS}
              value={form}
              onChange={setForm}
            />
            <Segmented
              testIDPrefix="cenario-04-tema"
              options={THEME_OPTIONS}
              value={choice}
              onChange={setChoice}
            />
            <ActionButton
              testID="cenario-04-abrir"
              label="Abrir pesquisa"
              disabled={gorhomOpen}
              onPress={openSurvey}
            />
            {selectedSurvey !== null && (
              <Hint>Dispara {selectedSurvey.triggerEvent}.</Hint>
            )}
            <ResetHint />
          </>
        )}
      </Screen>
      {/* Um BottomSheetModal novo por exibição (ver o cenário 2): a mesma instância não reabria. */}
      <GorhomSurveySheet
        key={`gorhom-${arrivedId ?? "nenhuma"}`}
        open={gorhomOpen}
        onClosed={finish}
        backgroundColor={colors.background}
        handleColor={colors.handle}
      />
    </>
  );
}
