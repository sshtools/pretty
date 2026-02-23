package com.sshtools.pretty.pricli;

import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import javafx.scene.input.ClipboardContent;
import picocli.CommandLine.Command;
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

	@Override
	public Integer call() throws Exception {
		var buf = parent.tty().terminal().getViewport();
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
