package com.sshtools.pretty.pricli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "record",
         aliases = {"rec" },
         footer = "%nAliases: rec", 
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Record all sequences received to a file.")
public class Record implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Record.class.getName());

	@Option(names = { "-p", "--printable-only" }, description = "Only record printable characters")
	private boolean printableOnly;

	@Parameters(index = "0", arity = "1", paramLabel = "PATH", description = "Location of recorded file", defaultValue = "term.dat")
	private Path path;

	@ParentCommand
	private TerminalCommands parent;

	@Override
	public Integer call() throws Exception {
		var buffer = parent.tty().terminal().getViewport();
		if (buffer.getRecordingWriter() == null) {
			buffer.setRecordPrintableOnly(printableOnly);
			buffer.startRecording(Files.newBufferedWriter(path));
		} else
			throw new IllegalStateException(RESOURCES.getString("alreadyRecording"));
		parent.cli().result(MessageFormat.format(RESOURCES.getString("recording"), path.getFileName().toString()));
		return 0;
	}
}
