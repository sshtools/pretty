package com.sshtools.pretty.pricli;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import com.sshtools.pretty.pricli.TerminalCommands.TerminalViewportType;

import javafx.scene.input.ClipboardContent;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
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
	
	@Parameters(index = "0", arity="0..1", paramLabel="CONTENT", description = "Place this content in the clipboard instead of from the selection.")
	private List<String> content = new ArrayList<>();

	@Override
	public Integer call() throws Exception {

		var clipContent = new ClipboardContent();
		String sel;
		if(content.isEmpty()) {
			var buf = parent.getTarget(source);
			sel = buf.getSelection();
			if (sel == null)
				throw new IllegalStateException(RESOURCES.getString("empty"));
		}
		else {
			sel = String.join(" ", content);
		}
		clipContent.putString(sel);
		parent.tty().setClipboard(clipContent);
		parent.cli().result(MessageFormat.format(RESOURCES.getString("copied"), sel.length()));
		return 0;
	}
}
