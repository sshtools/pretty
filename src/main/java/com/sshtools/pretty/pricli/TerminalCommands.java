package com.sshtools.pretty.pricli;

import java.util.Optional;

import com.sshtools.pretty.Actions.On;
import com.sshtools.pretty.Constants;
import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TTYContext;
import com.sshtools.terminal.emulation.TerminalViewport;

import picocli.CommandLine.Command;

@Command(name = "terminal", description = "Commands to manipulate or query the terminal", subcommands = {
		SelectAll.class, Scroll.class, ClearTerminal.class, Reset.class, Copy.class, Paste.class, Themes.class,
		Record.class, Stop.class, Tab.class })
public final class TerminalCommands extends AbstractRootCommand {
	public enum TerminalViewportType {
		TERMINAL, CLI
	}

	TerminalCommands(TTYContext ttyContext, PricliShell screen, TTY tty) {
		super(ttyContext, screen, tty);
	}

	public TerminalViewport<?, ?, ?> getTarget(Optional<TerminalViewportType> source) {

		if (source.isEmpty()) {
			var on = (On) cli().systemRegistry().consoleEngine().getVariable(Constants.ACTION_ON_VAR);
			switch (on) {
			case CLI_CONTEXT:
				return tty().cli().terminal().getViewport();
			default:
				return tty().terminal().getViewport();
			}
		} else {
			switch (source.get()) {
			case CLI:
				return tty().terminal().getViewport();
			default:
				return tty().cli().terminal().getViewport();
			}
		}
	}

}