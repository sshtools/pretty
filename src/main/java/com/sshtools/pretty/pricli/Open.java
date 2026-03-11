package com.sshtools.pretty.pricli;

import java.util.concurrent.Callable;

import javafx.application.Platform;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "open", 
         aliases = { "o" },
         footer = "%nAliases: o",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Open the provided document on the desktop given its URI")
public class Open implements Callable<Integer> {

	@ParentCommand
	private TerminalCommands parent;
	
	@Parameters(index = "0", arity="1", paramLabel="URI", description = "URI of document to open.")
	private String uri;

	@Override
	public Integer call() throws Exception {
		Platform.runLater(() -> {
			parent.tty().ttyContext().getContainer().getHostServices().showDocument(uri);
		});
		return 0;
	}
}
