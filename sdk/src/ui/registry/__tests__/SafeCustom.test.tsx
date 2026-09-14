import { render, screen } from '@testing-library/react-native';
import type { ReactNode } from 'react';
import { Text } from 'react-native';
import { PitacoContext, type PitacoContextValue } from '../../../react/context';
import { SafeCustom } from '../SafeCustom';

function contextValue(reportRenderError: jest.Mock): PitacoContextValue {
  return {
    runtime: null,
    controller: null,
    reportRenderError,
    ui: { presentation: 'bottom-sheet' },
  };
}

interface Props {
  readonly label: string;
}

function Default(props: Props): ReactNode {
  return <Text>padrão: {props.label}</Text>;
}

function Working(props: Props): ReactNode {
  return <Text>substituído: {props.label}</Text>;
}

function Broken(): ReactNode {
  throw new Error('renderizador substituído quebrado');
}

describe('SafeCustom (isolamento de renderizador/slot substituído)', () => {
  it('sem substituição, renderiza direto o padrão', async () => {
    await render(<SafeCustom kind="renderer" name="nps" custom={undefined} Default={Default} props={{ label: 'x' }} />);
    expect(screen.getByText('padrão: x')).toBeTruthy();
  });

  it('com substituição que funciona, renderiza o substituto', async () => {
    await render(<SafeCustom kind="renderer" name="nps" custom={Working} Default={Default} props={{ label: 'x' }} />);
    expect(screen.getByText('substituído: x')).toBeTruthy();
  });

  it('substituição que lança cai no padrão e reporta o erro do SDK', async () => {
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    const reportRenderError = jest.fn();
    await render(
      <PitacoContext.Provider value={contextValue(reportRenderError)}>
        <SafeCustom kind="renderer" name="nps" custom={Broken} Default={Default} props={{ label: 'x' }} />
      </PitacoContext.Provider>,
    );
    expect(screen.getByText('padrão: x')).toBeTruthy();
    expect(reportRenderError).toHaveBeenCalledWith(expect.any(Error), { kind: 'renderer', name: 'nps' });
    errorSpy.mockRestore();
  });

  it('o mesmo isolamento vale para slots substituídos', async () => {
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    const reportRenderError = jest.fn();
    await render(
      <PitacoContext.Provider value={contextValue(reportRenderError)}>
        <SafeCustom kind="slot" name="Footer" custom={Broken} Default={Default} props={{ label: 'y' }} />
      </PitacoContext.Provider>,
    );
    expect(screen.getByText('padrão: y')).toBeTruthy();
    expect(reportRenderError).toHaveBeenCalledWith(expect.any(Error), { kind: 'slot', name: 'Footer' });
    errorSpy.mockRestore();
  });
});
