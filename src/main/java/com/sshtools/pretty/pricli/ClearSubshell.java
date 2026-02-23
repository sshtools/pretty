package com.sshtools.pretty.pricli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(name = "clear-subshell", 
         aliases = { "clp", "cll" },
         footer = "%nAliases: clp",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Clear the subshell screen.")
public class ClearSubshell implements Callable<Integer> {
	
	@ParentCommand
	private PricliCommands parent;
	
	@Override
	public Integer call() throws Exception {
		var buf = parent.cli().terminal().getViewport();
		buf.enqueue(buf::clearScreen);
		return 0;
	}
}
