package com.sshtools.pretty.pricli;

import static javafx.application.Platform.runLater;

import java.text.MessageFormat;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(name = "copy", aliases = {
		"c" }, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Copy the selection to the system clipboard")
public class Copy implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Copy.class.getName());

	@ParentCommand
	private PricliCommands parent;

	@Override
	public Integer call() throws Exception {
		var buf = parent.tty().terminal().getViewport();
		String sel;
		synchronized (buf.getBufferLock()) {
			sel = buf.getSelection();
		}
		if (sel == null)
			throw new IllegalStateException(RESOURCES.getString("empty"));
		runLater(() -> Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.PLAIN_TEXT, sel)));
		parent.cli().result(MessageFormat.format(RESOURCES.getString("copied"), sel.length()));
		return 0;
	}
}
