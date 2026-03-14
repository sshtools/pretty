package com.sshtools.pretty.pricli;

import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TTYContext;
import com.sshtools.pretty.pricli.serial.Serial;
import com.sshtools.pretty.pricli.ssh.Ssh;

import picocli.CommandLine.Command;

@Command(name = "connections", description = "Commands that manage connections to other remote systems or devices", subcommands = {
		Serial.class, Ssh.class, Console.class })
public final class ConnectionCommands extends AbstractRootCommand {

	ConnectionCommands(TTYContext ttyContext, PricliShell screen, TTY tty) {
		super(ttyContext, screen, tty);
	}

}