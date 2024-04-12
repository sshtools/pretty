package com.sshtools.pretty.pricli;

import static javafx.application.Platform.runLater;

import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "set-tab", 
         aliases = { "st" },
         footer = "Aliases: st",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Configure a tab.")
public class SetTab implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(SetTab.class.getName());
	@ParentCommand
	private TerminalCommands parent;
	
	@Parameters(index = "0", arity="1", paramLabel="TITLE", description = "Title")
	private String title;

	@Override
	public Integer call() throws Exception {
		parent.cli().result(MessageFormat.format(RESOURCES.getString("titleSet"), parent.tty().protocol().displayName(), title));
		runLater(() -> {
			parent.tty().userTitle().set(title);
		});
		return 0;
	}
}
