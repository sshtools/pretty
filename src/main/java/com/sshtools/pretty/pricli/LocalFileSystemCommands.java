package com.sshtools.pretty.pricli;

import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TTYContext;

import picocli.CommandLine.Command;

@Command(name = "fs", description = "Commands that work on the local file system", subcommands = { Lcd.class, Lls.class,
		Lmkdir.class, Lpwd.class })
public final class LocalFileSystemCommands extends AbstractRootCommand {

	LocalFileSystemCommands(TTYContext ttyContext, PricliShell screen, TTY tty) {
		super(ttyContext, screen, tty);
	}

}