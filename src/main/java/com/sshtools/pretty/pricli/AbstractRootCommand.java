package com.sshtools.pretty.pricli;

import java.util.concurrent.Callable;

import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TTYContext;

import picocli.CommandLine.Command;

@Command
public abstract class AbstractRootCommand implements Callable<Integer>, RootCommand {

	private final TTYContext ttyContext;
	private final Pricli screen;
	private final TTY tty;

	AbstractRootCommand(TTYContext ttyContext, Pricli screen, TTY tty) {
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