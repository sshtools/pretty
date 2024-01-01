package com.sshtools.pretty.pricli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(name = "clear-screen", aliases = {"cls"}, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Clear the terminal screen.")
public class ClearScreen implements Callable<Integer> {
	
	@ParentCommand
	private PricliCommands parent;
	
	@Override
	public Integer call() throws Exception {
		var buf = parent.tty().terminal().getViewport();
		synchronized(buf.getBufferLock()) {
			buf.clearScreen();
		}
		return 0;
	}
}
