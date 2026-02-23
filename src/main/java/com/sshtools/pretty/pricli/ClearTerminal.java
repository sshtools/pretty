package com.sshtools.pretty.pricli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(name = "clear-terminal", 
         aliases = { "cls", "ct" },
         footer = "%nAliases: cls, ct",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Clear the terminal screen.")
public class ClearTerminal implements Callable<Integer> {
	
	@ParentCommand
	private TerminalCommands parent;
	
	@Override
	public Integer call() throws Exception {
		var buf = parent.tty().terminal().getViewport();
		buf.enqueue(buf::clearScreen);
		return 0;
	}
}
