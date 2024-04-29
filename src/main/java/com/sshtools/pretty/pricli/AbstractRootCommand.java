package com.sshtools.pretty.pricli;

import java.util.concurrent.Callable;

import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TTYContext;

import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;

@Command
public abstract class AbstractRootCommand implements Callable<Integer>, RootCommand {

	private final TTYContext ttyContext;
	private final PricliShell screen;
	private final TTY tty;
	
	@Spec
	protected CommandSpec spec;

	AbstractRootCommand(TTYContext ttyContext, PricliShell screen, TTY tty) {
		this.ttyContext = ttyContext;
		this.screen = screen;
		this.tty = tty;
	}

	@Override
	public PricliShell cli() {
		return screen;
	}

	public TTY tty() {
		return tty;
	}

	public TTYContext ttyContext() {
		return ttyContext;
	}

	@Override
	public final Integer call() throws Exception {  
		spec.commandLine().usage(tty.cli().jline().writer(), Ansi.ON);
		return 0;
	}

}