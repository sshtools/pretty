package com.sshtools.pretty.pricli;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import com.sshtools.pretty.pricli.TerminalCommands.TerminalViewportType;

import javafx.scene.input.ClipboardContent;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(name = "copy", 
         aliases = { "c" },
         footer = "%nAliases: c",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Copy the selection to the system clipboard")
public class Copy implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Copy.class.getName());
	

	@ParentCommand
	private TerminalCommands parent;
	
	@Option(names = { "-s", "--source" }, description = "Source of selection. When not specified, based on the current context.")
	private Optional<TerminalViewportType> source;

	@Override
	public Integer call() throws Exception {

		var buf = parent.getTarget(source);
		var sel = buf.getSelection();
		if (sel == null)
			throw new IllegalStateException(RESOURCES.getString("empty"));
		var content = new ClipboardContent();
		content.putString(sel);
		parent.tty().setClipboard(content);
		parent.cli().result(MessageFormat.format(RESOURCES.getString("copied"), sel.length()));
		return 0;
	}
}
