package com.sshtools.pretty.pricli;

import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TTYContext;

import picocli.CommandLine.Command;

@Command(name = "", description = "Pricli, (or 'Pretty Interactive Command Line Interface')", 
		subcommands = {
		About.class,  Actions.class,
		Set.class, Options.class, Quit.class, ClearSubshell.class, Update.class, Subshell.class })
public final class PricliCommands extends AbstractRootCommand {

	PricliCommands(TTYContext ttyContext, PricliShell screen, TTY tty) {
		super(ttyContext, screen, tty);
	}

}