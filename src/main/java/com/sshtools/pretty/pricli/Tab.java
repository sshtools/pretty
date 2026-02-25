package com.sshtools.pretty.pricli;

import static javafx.application.Platform.runLater;

import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import com.sshtools.pretty.Colors;
import com.sshtools.pretty.TTYRequest;

import javafx.geometry.Orientation;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "tab", 
         aliases = { "t" },
         footer = "Aliases: t",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Various commands for tabs.", 
                 subcommands = {Tab.Name.class, Tab.Detach.class, Tab.Color.class, Tab.Rename.class, Tab.SplitHorizontal.class, Tab.SplitVertical.class, Tab.Close.class})
public class Tab implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Tab.class.getName());
	@ParentCommand
	private TerminalCommands parent;
	
	@Spec
	private CommandSpec spec;

	@Override
	public final Integer call() throws Exception {  
		spec.commandLine().usage(parent.cli().jline().writer(), Ansi.ON);
		return 0;
	}
	
	@Command(name = "name", 
	         aliases = { "n" },
	         footer = "Aliases: n",
	         usageHelpAutoWidth = true, 
	         
	         description = "Set or get the tab name.")
	public final static class Name implements Callable<Integer> {
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
	
	@Command(name = "color", 
	         aliases = { "c" },
	         footer = "Aliases: c",
	         usageHelpAutoWidth = true, 
	         
	         description = "Set or get the tab color.")
	public final static class Color implements Callable<Integer> {
		@ParentCommand
		private Tab parent;
		
		@Parameters(index = "0", arity="0..1", paramLabel="COLOR", description = "Color")
		private String color;

		@Override
		public Integer call() throws Exception {
			if(color == null) {
				var col = parent.parent.tty().tabColor();
				if(col.get() == null)
					parent.parent.cli().jline().writer().println(RESOURCES.getString("defaultColor"));
				else {
					parent.parent.cli().jline().writer().println(Colors.toHex(col.get())); 
				}
			}
			else {
				if(color.startsWith("#")) {
					runLater(() -> {
						parent.parent.tty().tabColor().set(Colors.parse(color, null));
					});
				}
				else {
					var idx = Integer.parseInt(color);
					parent.parent.ttyContext().getContainer().getSelectedTheme().pal16().ifPresent(pals -> {
						runLater(() -> {
							parent.parent.tty().tabColor().set(parent.parent.tty().terminal().getUIToolkit().localColorToNativeColor(pals[idx]));
						});
					});
				}
				parent.parent.cli().result(MessageFormat.format(RESOURCES.getString("colorSet"), parent.parent.tty().protocol().displayName(), color));
			}
			return 0;
		}
	}
	
	@Command(name = "detach", 
	         aliases = { "d" },
	         footer = "Aliases: d",
	         usageHelpAutoWidth = true, 
	         
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
	
	@Command(name = "rename", 
	         aliases = { "r" },
	         footer = "Aliases: r",
	         usageHelpAutoWidth = true, 
	         
	         description = "Trigger the tab name editor for the current tab..")
	public final static class Rename implements Callable<Integer> {
		@ParentCommand
		private Tab parent;
		
		@Override
		public Integer call() throws Exception {
			runLater(() -> {
				parent.parent.cli().close();
				runLater(() -> {
					parent.parent.ttyContext().renameTab(parent.parent.tty());
				});
			});
			return 0;
		}
	}
	
	@Command(name = "split-horizontal", 
	         aliases = { "sh" },
	         footer = "Aliases: sh",
	         usageHelpAutoWidth = true, 
	         
	         description = "Split the currently active tty horizontally and create a new session.")
	public final static class SplitHorizontal implements Callable<Integer> {
		@ParentCommand
		private Tab parent;
		
		@Override
		public Integer call() throws Exception {
			runLater(() -> {
				parent.parent.cli().close();
				runLater(() -> {
					parent.parent.ttyContext().splitTab(
							parent.parent.tty(), 
							new TTYRequest.Builder().build(),
							Orientation.HORIZONTAL);
				});
			});
			return 0;
		}
	}
	
	@Command(name = "split-vertical", 
	         aliases = { "sv" },
	         footer = "Aliases: sv",
	         usageHelpAutoWidth = true, 
	         
	         description = "Split the currently active tty vertically and create a new session.")
	public final static class SplitVertical implements Callable<Integer> {
		@ParentCommand
		private Tab parent;
		
		@Override
		public Integer call() throws Exception {
			runLater(() -> {
				parent.parent.cli().close();
				runLater(() -> {
					parent.parent.ttyContext().splitTab(
							parent.parent.tty(), 
							new TTYRequest.Builder().build(),
							Orientation.VERTICAL);
				});
			});
			return 0;
		}
	}
	
	@Command(name = "close", 
	         aliases = { "cl" },
	         footer = "Aliases: cl",
	         usageHelpAutoWidth = true, 
	         
	         description = "Close the current split or tab.")
	public final static class Close implements Callable<Integer> {
		@ParentCommand
		private Tab parent;
		
		@Override
		public Integer call() throws Exception {
			runLater(() -> {
				parent.parent.cli().close();
				runLater(() -> {
					parent.parent.tty().close();
				});
			});
			return 0;
		}
	}
	
}
