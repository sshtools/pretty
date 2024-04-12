package com.sshtools.pretty.pricli;

import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TTYContext;

import picocli.CommandLine.Command;

@Command(name = "debug", description = "Commands for debugging and troubleshooting this application", subcommands = {
		Features.class, Images.class, Debug.class, Fonts.class, Charmap.class })
public final class DebugCommands extends AbstractRootCommand {

	DebugCommands(TTYContext ttyContext, PricliShell screen, TTY tty) {
		super(ttyContext, screen, tty);
	}

}