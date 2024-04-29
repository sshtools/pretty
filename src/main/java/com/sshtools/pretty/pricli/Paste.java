package com.sshtools.pretty.pricli;

import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import javafx.scene.input.Clipboard;
import picocli.CommandLine.Command;
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

	@Override
	public Integer call() throws Exception {
		var pasted = parent.tty().clipboardToHost(Clipboard.getSystemClipboard());
		parent.cli().result(MessageFormat.format(RESOURCES.getString("pasted"), pasted));
		return 0;
	}
}
