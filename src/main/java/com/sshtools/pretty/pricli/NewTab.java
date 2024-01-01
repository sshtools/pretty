package com.sshtools.pretty.pricli;

import static javafx.application.Platform.runLater;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(name = "new-tab", 
         aliases = { "nt" },
         footer = "Aliases: nt",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Open a new tab.")
public class NewTab implements Callable<Integer> {
	@ParentCommand
	private UICommands parent;

	@Override
	public Integer call() throws Exception {
		runLater(() -> {
			parent.ttyContext().newTab();
		});
		return 0;
	}
}
