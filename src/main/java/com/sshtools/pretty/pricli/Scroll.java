package com.sshtools.pretty.pricli;

import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Help.Ansi;

@Command(name = "scroll", 
         aliases = {"scr"},
         footer = "%nAliases: scr", 
         usageHelpAutoWidth = true, 
//         helpCommand = true,
         mixinStandardHelpOptions = true,
         description = "Adjust the current viewport, scrolling back or forward through the scroll back buffer.",
         subcommands = { Scroll.Top.class, Scroll.Bottom.class, Scroll.Lines.class,Scroll.Pages.class })
public class Scroll implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Scroll.class.getName());
	
	@ParentCommand
	private TerminalCommands parent;  
	
	@Spec
	private CommandSpec spec;  
	
	@Override
	public Integer call() throws Exception {
		spec.commandLine().usage(parent.cli().jline().writer(), Ansi.ON);
		return 0;
	}
    
    @Command(name = "top", aliases = {"t", "to", "tp", "s", "start"}, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Scroll to top.")
	public final static class Top implements Callable<Integer> {

		@ParentCommand
		private Scroll parent;
		
		@Override
		public Integer call() throws Exception {
			parent.parent.tty().terminal().getViewport().setWindowBase(0);
			parent.parent.cli().result(RESOURCES.getString("scrolledToTop"));
			return  0;
		}

	}
    
    @Command(name = "bottom", aliases = {"b", "bo", "bt", "btm", "end", "e"}, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Scroll to bottom.")
	public final static class Bottom implements Callable<Integer> {

		@ParentCommand
		private Scroll parent;
		
		@Override
		public Integer call() throws Exception {
			parent.parent.tty().terminal().getViewport().scrollToEnd();
			parent.parent.cli().result(RESOURCES.getString("scrolledToBottom"));
			return 0;
		}

	}
    
    @Command(name = "lines", aliases = {"l", "ln", "lns", "line"}, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Scroll an amount of lines.")
	public final static class Lines implements Callable<Integer> {

		@ParentCommand
		private Scroll parent;
		
	    @Parameters(arity = "1", description = "Number of lines to scroll forward. Number may be negative to scroll forward.")
	    private int lines;
		
		@Override
		public Integer call() throws Exception {
			var vp = parent.parent.tty().terminal().getViewport();
			vp.setWindowBase(vp.getPage().windowBase() + lines);
			parent.parent.cli().result(MessageFormat.format(RESOURCES.getString("scrolledLines"), lines));
			return 0;
		}

	}
    
    @Command(name = "pages", aliases = {"p", "pg", "pgs", "page"}, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Scroll an amount of pages.")
	public final static class Pages implements Callable<Integer> {

		@ParentCommand
		private Scroll parent;
		
	    @Parameters(arity = "1", description = "Number of pages to scroll forward. Number may be negative to scroll forward.")
	    private int pages;
		
		@Override
		public Integer call() throws Exception {
			var vp = parent.parent.tty().terminal().getViewport();
			vp.setWindowBase(vp.getPage().windowBase() + (pages * vp.getRows()));
			parent.parent.cli().result(MessageFormat.format(RESOURCES.getString("scrolledPages"), pages));
			return 0;
		}
	}
}
