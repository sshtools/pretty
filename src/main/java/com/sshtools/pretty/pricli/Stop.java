package com.sshtools.pretty.pricli;

import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(name = "stop", 
        aliases = { "sr" },
        footer = "%nAliases: sr",
        usageHelpAutoWidth = true, 
        mixinStandardHelpOptions = true, 
        description = "Stop recording.")
public class Stop implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Stop.class.getName());

	@ParentCommand
	private TerminalCommands parent;

	@Override
	public Integer call() throws Exception {
		var buffer = parent.tty().terminal().getViewport();
		if (buffer.getRecordingWriter() == null) {
			throw new IllegalStateException(RESOURCES.getString("notRecording"));
		}
		buffer.stopRecording();
		parent.cli().result(RESOURCES.getString("stopped"));
		return 0;
	}
}
