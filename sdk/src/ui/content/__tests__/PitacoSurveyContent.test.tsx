import { act, fireEvent, render, screen } from '@testing-library/react-native';
import { useState } from 'react';
import type { InteractionEvent } from '../../../catalog/events';
import { deliverableSurvey } from '../../../__tests__/support/fixtures';
import { PitacoPreviewProvider } from '../../../preview';
import { usePitacoSurvey } from '../../../react/usePitacoSurvey';
import { PitacoSurveyContent } from '../PitacoSurveyContent';

const NOW = Date.UTC(2026, 8, 12, 13, 0, 0);

function Harness(props: { readonly onFinish: jest.Mock; readonly onEvent: (event: InteractionEvent) => void }) {
  return (
    <PitacoPreviewProvider schema={deliverableSurvey()} onEvent={props.onEvent}>
      <PitacoSurveyContent onFinish={props.onFinish} />
    </PitacoPreviewProvider>
  );
}

// Espião headless: usado para checar o estado do core (via) sem depender só da UI.
function StatusSpy(props: { readonly onStatus: (status: string) => void }) {
  const survey = usePitacoSurvey();
  props.onStatus(survey.status);
  return null;
}

beforeEach(() => {
  jest.useFakeTimers({ now: NOW });
});

afterEach(() => {
  jest.useRealTimers();
});

describe('<PitacoSurveyContent />', () => {
  it('chama present() ao ficar visível e percorre a pesquisa com os renderizadores provisórios até o agradecimento', async () => {
    const onFinish = jest.fn();
    const events: InteractionEvent[] = [];
    await render(<Harness onFinish={onFinish} onEvent={(event) => events.push(event)} />);

    // `present()` chamado ao montar: abre a exibição.
    expect(events.some((event) => event.type === 'survey_presented')).toBe(true);

    // NPS, obrigatória: avançar sem responder bloqueia.
    expect(screen.getByText(/O quanto você recomendaria/)).toBeTruthy();
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    expect(screen.getByText('Essa pergunta é obrigatória.')).toBeTruthy();

    // Responde 9: a pergunta de motivo (condição 0-6) fica não aplicável e é pulada sozinha.
    await act(async () => fireEvent.press(screen.getByRole('radio', { name: '9' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    expect(screen.getByText(/O que você usa/)).toBeTruthy();

    // Múltipla escolha, opcional: avança em branco.
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    expect(screen.getByText(/Como avalia o app/)).toBeTruthy();

    // Avaliação, obrigatória.
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    expect(screen.getByText('Essa pergunta é obrigatória.')).toBeTruthy();
    await act(async () => fireEvent.press(screen.getByRole('radio', { name: '4 de 5' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));

    // Texto livre, opcional e última: o rótulo já é "Enviar".
    expect(screen.getByText(/Quer contar mais/)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Enviar' })).toBeTruthy();
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Enviar' })));

    // Tela de agradecimento, que fecha sozinha.
    expect(screen.getByText('Obrigado!')).toBeTruthy();
    expect(onFinish).not.toHaveBeenCalled();
    await act(async () => jest.advanceTimersByTimeAsync(2500));
    expect(onFinish).toHaveBeenCalledWith('completed');
    expect(events.some((event) => event.type === 'survey_completed')).toBe(true);
  });

  it('o botão de fechar dispensa com via close_button e chama onFinish("dismissed")', async () => {
    const onFinish = jest.fn();
    const events: InteractionEvent[] = [];
    await render(<Harness onFinish={onFinish} onEvent={(event) => events.push(event)} />);

    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Fechar pesquisa' })));

    expect(onFinish).toHaveBeenCalledWith('dismissed');
    const dismissed = events.find((event) => event.type === 'survey_dismissed');
    expect(dismissed).toBeDefined();
    expect((dismissed as { data: { via: string } }).data.via).toBe('close_button');
  });

  it('ao desmontar com a pesquisa em andamento, dispensa com via "navigation" por padrão', async () => {
    // O Provider (aqui, o preview) fica montado — o mesmo do cenário 3 do guia: uma rota some da
    // navegação, mas o Provider da raiz do app continua vivo.
    const onFinish = jest.fn();
    const events: InteractionEvent[] = [];
    let status = 'idle';
    function Screen() {
      const [showContent, setShowContent] = useState(true);
      return (
        <>
          {showContent ? <PitacoSurveyContent onFinish={onFinish} /> : null}
          <StatusSpy onStatus={(value) => (status = value)} />
          <UnmountTrigger onTrigger={() => setShowContent(false)} />
        </>
      );
    }
    let triggerUnmount = () => undefined as void;
    function UnmountTrigger(props: { readonly onTrigger: () => void }) {
      triggerUnmount = props.onTrigger;
      return null;
    }

    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()} onEvent={(event) => events.push(event)}>
        <Screen />
      </PitacoPreviewProvider>,
    );

    expect(status).toBe('presented');
    await act(async () => triggerUnmount());

    expect(onFinish).toHaveBeenCalledWith('dismissed');
    const dismissed = events.find((event) => event.type === 'survey_dismissed');
    expect(dismissed).toBeDefined();
    expect((dismissed as { data: { via: string } }).data.via).toBe('navigation');
  });

  it('não dispensa de novo ao desmontar depois de concluída', async () => {
    const onFinish = jest.fn();
    const events: InteractionEvent[] = [];
    const { unmount } = await render(<Harness onFinish={onFinish} onEvent={(event) => events.push(event)} />);

    await act(async () => fireEvent.press(screen.getByRole('radio', { name: '9' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    await act(async () => fireEvent.press(screen.getByRole('radio', { name: '4 de 5' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Próxima' })));
    await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Enviar' })));

    onFinish.mockClear();
    await act(async () => unmount());

    expect(onFinish).not.toHaveBeenCalled();
    expect(events.filter((event) => event.type === 'survey_dismissed')).toHaveLength(0);
  });

  it('renderers/slots locais sobrepõem os do Provider (aceitos como props)', async () => {
    function CustomFooter() {
      return null;
    }
    await render(
      <PitacoPreviewProvider schema={deliverableSurvey()}>
        <PitacoSurveyContent slots={{ Footer: CustomFooter }} />
      </PitacoPreviewProvider>,
    );
    expect(screen.queryByRole('button', { name: 'Próxima' })).toBeNull();
  });
});
