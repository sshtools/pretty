package com.sshtools.pretty.pricli;

import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TTYContext;

import picocli.CommandLine.Command;

@Command(name = "ui", description = "Commands that manipulate the graphical user interface", 
		subcommands = {
		NewTab.class, NewWindow.class })
public final class UICommands extends AbstractRootCommand {

	UICommands(TTYContext ttyContext, Pricli screen, TTY tty) {
		super(ttyContext, screen, tty);
	}

}