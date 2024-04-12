package com.sshtools.pretty.pricli;

import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TTYContext;

import picocli.CommandLine.Command;

@Command(name = "", description = "Pricli, (or 'Pretty Interactive Command Line Interface')", 
		subcommands = {
		About.class,  
		SetConfig.class, Show.class, Quit.class, ClearSubshell.class, Update.class })
public final class PricliCommands extends AbstractRootCommand {

	PricliCommands(TTYContext ttyContext, PricliShell screen, TTY tty) {
		super(ttyContext, screen, tty);
	}

}