package com.sshtools.pretty.pricli;

import static javafx.application.Platform.runLater;

import java.util.Optional;
import java.util.concurrent.Callable;

import com.sshtools.pretty.TTYRequest;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(name = "new-window",
         aliases = { "nw" },
         footer = "%nAliases: nw",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Open a new window.")
public class NewWindow implements Callable<Integer> {
	@ParentCommand
	private UICommands parent;
	
	@Option(names = { "-t", "--type" }, description = "Set the terminal type for the new tab.")
	private Optional<String> type;

	@Override
	public Integer call() throws Exception {
		runLater(() -> {
			var bldr = new TTYRequest.Builder();
			type.ifPresent(bldr::withTermType);
			parent.ttyContext().newWindow(Optional.of(bldr.build()));
		});
		return 0;
	}
}
