package com.sshtools.pretty.pricli;

import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TTYContext;

import picocli.CommandLine.Command;

@Command(name = "terminal", description = "Commands to manipulate or query the terminal", 
		subcommands = {
		SelectAll.class, ClearTerminal.class, Reset.class,
		Copy.class, Themes.class, Record.class, Stop.class })
public final class TerminalCommands extends AbstractRootCommand {

	TerminalCommands(TTYContext ttyContext, Pricli screen, TTY tty) {
		super(ttyContext, screen, tty);
	}

}