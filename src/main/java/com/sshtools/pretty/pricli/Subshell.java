package com.sshtools.pretty.pricli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "subshell", 
         aliases = { "sshl" },
         footer = "Aliases: sshl",
         usageHelpAutoWidth = true, 
         
         description = "Various commands for the subshell.", 
                 subcommands = {Subshell.Hide.class, Subshell.Show.class, Subshell.Toggle.class})
public class Subshell implements Callable<Integer> {
	@ParentCommand
	private PricliCommands parent;

	@Spec
	private CommandSpec spec;

	@Override
	public final Integer call() throws Exception {  
		spec.commandLine().usage(parent.cli().jline().writer(), Ansi.ON);
		return 0;
	}
	
	@Command(name = "hide", 
	         aliases = { "h" },
	         footer = "Aliases: h",
	         usageHelpAutoWidth = true, 
	         
	         description = "Hide the subshell.")
	public final static class Hide implements Callable<Integer> {
		@ParentCommand
		private Subshell parent;
		
		@Override
		public Integer call() throws Exception {
			parent.parent.tty().hidePricli();
			return 0;
		}
	}
	
	@Command(name = "show", 
	         aliases = { "s" },
	         footer = "Aliases: s",
	         usageHelpAutoWidth = true, 
	         
	         description = "Show the subshell.")
	public final static class Show implements Callable<Integer> {
		@ParentCommand
		private Subshell parent;
		
		@Override
		public Integer call() throws Exception {
			parent.parent.tty().showPricli();
			return 0;
		}
	}
	
	@Command(name = "toggle", 
	         aliases = { "t" },
	         footer = "Aliases: t",
	         usageHelpAutoWidth = true, 
	         
	         description = "Toggle the subshell.")
	public final static class Toggle implements Callable<Integer> {
		@ParentCommand
		private Subshell parent;
		
		@Override
		public Integer call() throws Exception {
			parent.parent.tty().togglePricli();
			return 0;
		}
	}

}
