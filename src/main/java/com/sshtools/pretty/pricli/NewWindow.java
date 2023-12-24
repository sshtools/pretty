package com.sshtools.pretty.pricli;

import static javafx.application.Platform.runLater;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(name = "new-window", aliases = { "nw" }, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Open a new window.")
public class NewWindow implements Callable<Integer> {
	@ParentCommand
	private Pricli.PricliCommands parent;

	@Override
	public Integer call() throws Exception {
		runLater(() -> {
			parent.ttyContext().newWindow();
		});
		return 0;
	}
}
