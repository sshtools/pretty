package com.sshtools.pretty.pricli;

import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TTYContext;
import com.sshtools.pretty.pricli.serial.XModemReceive;
import com.sshtools.pretty.pricli.serial.XModemSend;
import com.sshtools.pretty.pricli.serial.YModemReceive;
import com.sshtools.pretty.pricli.serial.YModemSend;
import com.sshtools.pretty.pricli.serial.ZModemReceive;
import com.sshtools.pretty.pricli.serial.ZModemSend;

import picocli.CommandLine.Command;

@Command(name = "fs", description = "Commands that work on the local file system", subcommands = { Lcd.class, Lls.class,
		Lmkdir.class, Lpwd.class, XModemSend.class, XModemReceive.class, YModemSend.class, YModemReceive.class, ZModemSend.class, ZModemReceive.class })
public final class LocalFileSystemCommands extends AbstractRootCommand {

	LocalFileSystemCommands(TTYContext ttyContext, PricliShell screen, TTY tty) {
		super(ttyContext, screen, tty);
	}

}