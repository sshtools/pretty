package com.sshtools.pretty.pricli;

import java.util.Optional;
import java.util.concurrent.Callable;

import com.sshtools.terminal.emulation.Dim;
import com.sshtools.terminal.emulation.GraphicsMode;
import com.sshtools.terminal.emulation.decoder.tektronix.TekState;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "tektronix", aliases = {
		"tek" }, footer = "%nAliases: tek", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Control Tektronix emulation.")
public class Tektronix implements Callable<Integer> {

	public enum Operation {
		SHOW, HIDE
	}

	@ParentCommand
	private TerminalCommands parent;

	@Parameters(index = "0", arity = "1", paramLabel = "OPERATION", description = """
			SHOW to show the Tekronix graphics canvas, HIDE to hide it.
			""")
	private Operation operation = Operation.SHOW;

	@Parameters(index = "1", arity = "0..1", paramLabel = "MODE", description = """
			WINDOW, OVERLAY, UNDERLAY, REPLACE or OFF.
			""")
	private Optional<GraphicsMode> mode = Optional.empty();

	@Override
	public Integer call() throws Exception {
		var terminal = parent.tty().terminal();
		switch (operation) {
		case SHOW -> {
			terminal.startGraphicsCanvas(new Dim(TekState.TEK_WIDTH, TekState.TEK_HEIGHT));
			terminal.setGraphicsMode(mode.orElse(GraphicsMode.WINDOW));
		}
		case HIDE -> terminal.endGraphicsCanvas();
		}
		return 0;
	}
}
