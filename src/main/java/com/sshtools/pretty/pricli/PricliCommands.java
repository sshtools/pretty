package com.sshtools.pretty.pricli;

import java.util.concurrent.Callable;

import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TTYContext;
import com.sshtools.pretty.pricli.ssh.Ssh;

import picocli.CommandLine.Command;
import picocli.shell.jline3.PicocliCommands.ClearScreen;

@Command(name = Pricli.PRICLI_SECTION, description = "Pricli, (or 'Pretty Interactive Command Line Interface')", 
		subcommands = {
		About.class, Record.class, Lcd.class, Lls.class, Lmkdir.class, Lpwd.class, SelectAll.class, ClearScreen.class, Stop.class, Reset.class,
		Serial.class, Ssh.class, SetConfig.class, Show.class, Copy.class, Quit.class, Features.class, Images.class, Fonts.class, Charmap.class, Themes.class,
				Debug.class, Console.class/* , , NewTab.class, NewWindow.classHelpCommand.class */ })
public final class PricliCommands implements Callable<Integer>, RootCommand {

	private final TTYContext ttyContext;
	private final Pricli screen;
	private final TTY tty;

	PricliCommands(TTYContext ttyContext, Pricli screen, TTY tty) {
		this.ttyContext = ttyContext;
		this.screen = screen;
		this.tty = tty;
	}

	@Override
	public Pricli cli() {
		return screen;
	}

	public TTY tty() {
		return tty;
	}

	public TTYContext ttyContext() {
		return ttyContext;
	}

	@Override
	public Integer call() throws Exception {
		return 0;
	}

}