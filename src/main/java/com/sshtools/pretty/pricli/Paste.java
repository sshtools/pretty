package com.sshtools.pretty.pricli;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import com.sshtools.pretty.pricli.TerminalCommands.TerminalViewportType;

import javafx.scene.input.Clipboard;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(name = "paste", 
         aliases = { "p" },
         footer = "%nAliases: p",
         usageHelpAutoWidth = true, 
         
         description = "Paste the clipboard contents as terminal input")
public class Paste implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Paste.class.getName());

	@ParentCommand
	private TerminalCommands parent;
	
	@Option(names = { "-t", "--target" }, description = "Target of paste. When not specified, based on the current context.")
	private Optional<TerminalViewportType> target;

	@Override
	public Integer call() throws Exception {
		var pasted = parent.tty().clipboardToHost(parent.getTarget(target), Clipboard.getSystemClipboard());
		parent.cli().result(MessageFormat.format(RESOURCES.getString("pasted"), pasted));
		return 0;
	}
}
