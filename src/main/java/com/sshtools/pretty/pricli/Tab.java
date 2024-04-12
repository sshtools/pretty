package com.sshtools.pretty.pricli;

import static javafx.application.Platform.runLater;

import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "tab", 
         aliases = { "t" },
         footer = "Aliases: t",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Various commands for tabs.", 
                 subcommands = {Tab.Title.class, Tab.Detach.class})
public class Tab implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Tab.class.getName());
	@ParentCommand
	private TerminalCommands parent;
	
	@Override
	public Integer call() throws Exception {
		throw new IllegalStateException("Sub-command required.");
	}
	
	@Command(name = "title", 
	         aliases = { "t" },
	         footer = "Aliases: t",
	         usageHelpAutoWidth = true, 
	         mixinStandardHelpOptions = true, 
	         description = "Set or get the tab title.")
	public final static class Title implements Callable<Integer> {
		@ParentCommand
		private Tab parent;
		
		@Parameters(index = "0", arity="0..1", paramLabel="TITLE", description = "Title")
		private String title;

		@Override
		public Integer call() throws Exception {
			if(title == null) {
				parent.parent.cli().jline().writer().println(parent.parent.tty().shortTitle().get());
			}
			else {
				parent.parent.cli().result(MessageFormat.format(RESOURCES.getString("titleSet"), parent.parent.tty().protocol().displayName(), title));
				runLater(() -> {
					parent.parent.tty().userTitle().set(title);
				});
			}
			return 0;
		}
	}
	
	@Command(name = "detach", 
	         aliases = { "d" },
	         footer = "Aliases: d",
	         usageHelpAutoWidth = true, 
	         mixinStandardHelpOptions = true, 
	         description = "Detach the tab, moving it to a new window.")
	public final static class Detach implements Callable<Integer> {
		@ParentCommand
		private Tab parent;
		
		@Override
		public Integer call() throws Exception {
			parent.parent.cli().result(MessageFormat.format(RESOURCES.getString("detached"), parent.parent.tty().shortTitle().get()));
			runLater(() -> {
				parent.parent.ttyContext().detachTab(parent.parent.tty());
				parent.parent.cli().close();
			});
			return 0;
		}
	}
	
}
