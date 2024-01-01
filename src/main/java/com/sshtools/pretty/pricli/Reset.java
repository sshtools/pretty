package com.sshtools.pretty.pricli;

import static javafx.application.Platform.runLater;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(name = "reset", 
         aliases = { "rst", "r" },
         footer = "%nAliases: rst, r",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Reset the terminal (hard or soft).")
public class Reset implements Callable<Integer> {
	@Option(names = { "-h", "--hard" }, description = "Perform hard reset")
	private boolean hard;

	@ParentCommand
	private TerminalCommands parent;

	@Override
	public Integer call() throws Exception {

		var term = parent.tty().terminal();
		var buf = term.getViewport();
		synchronized(buf.getBufferLock()) {
			if(hard)
				buf.hardReset();
			else
				buf.softReset();
		}
		runLater(() -> {
			term.getAudio().beep();
		});
		return 0;
	}
}
