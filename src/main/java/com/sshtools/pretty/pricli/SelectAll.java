package com.sshtools.pretty.pricli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(name = "select-all", 
         aliases = {"sa"},
         footer = "%nAliases: sa", 
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Select all text on the screen.")
public class SelectAll implements Callable<Integer> {
	@ParentCommand
	private TerminalCommands parent;
	
	@Override
	public Integer call() throws Exception {
		var buf = parent.tty().terminal().getViewport();
		buf.enqueue(buf::selectAll);
		return 0;
	}
}
