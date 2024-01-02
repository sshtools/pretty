package com.sshtools.pretty.pricli;

import static com.sshtools.pretty.pricli.Styling.styled;
import static java.text.MessageFormat.format;

import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import com.sshtools.jaul.Phase;
import com.sshtools.pretty.Pretty;

import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "update", 
        aliases = { "upd" },
        footer = "%nAliases: upd",
        usageHelpAutoWidth = true, 
        mixinStandardHelpOptions = true, 
        description = "Check for and install updates.",
        subcommands = { Update.Check.class, Update.Install.class, Update.SetOrGetPhase.class, Update.ListPhases.class })
public class Update implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Update.class.getName());

	@ParentCommand
	private PricliCommands parent;
	
	@Spec
	private CommandSpec spec;

	@Override
	public Integer call() throws Exception {
		spec.commandLine().usage(parent.cli().jline().writer(), Ansi.ON);
		return 0;
	}
	
	@Command(name = "check", aliases = { "c", "chk" }, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Check for update to this application.")
	public static class Check implements Callable<Integer> {
		
		@ParentCommand
		private Update parent;
		
		@Override
		public Integer call() throws Exception {
			var cli = parent.parent.cli();
			var app = cli.tty().getTTYContext().getContainer();
			var updateService = app.getUpdateService();
			updateService.checkForUpdate();
			if(updateService.isNeedsUpdating()) {
				cli.print(styled(format(RESOURCES.getString("needsUpdate"), Pretty.getVersion()[0], updateService.getAvailableVersion())).toAttributedString());
			}
			else
				cli.print(styled(format(RESOURCES.getString("noUpdate"), Pretty.getVersion()[0])).toAttributedString());
			return 0;
		}
	}
	
	@Command(name = "install", aliases = { "i", "in" }, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Download (if required) and install the latest available version.")
	public static class Install implements Callable<Integer> {
		
		@ParentCommand
		private Update parent;
		
		@Override
		public Integer call() throws Exception {

			var cli = parent.parent.cli();
			var app = cli.tty().getTTYContext().getContainer();
			var updateService = app.getUpdateService();
			
			updateService.checkForUpdate();
			if (updateService.isNeedsUpdating()) {
				cli.print(styled(format(RESOURCES.getString("updating"), Pretty.getVersion()[0], updateService.getAvailableVersion())).toAttributedString());
				updateService.update();
			} else
				cli.print(styled(format(RESOURCES.getString("noUpdate"), Pretty.getVersion()[0])).toAttributedString());
			return 0;
		}
	}


	@Command(name = "phase", aliases = { "p"}, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Set or show the current update phase.")
	public static class SetOrGetPhase implements Callable<Integer> {

		@ParentCommand
		private Update parent;

		@Parameters(arity = "0..1", description = "Phase to set. If ommitted, current phase will be shown.")
		Optional<Phase> phase;

		@Override
		public Integer call() throws Exception {
			var cli = parent.parent.cli();
			var app = cli.tty().getTTYContext().getContainer();
			var updateService = app.getUpdateService();
			phase.ifPresentOrElse(p -> updateService.getContext().setPhase(p),
					() -> cli.result(styled(format(RESOURCES.getString("phase"), updateService.getContext().getPhase())).toAttributedString()));
			return 0;
		}
	}

	@Command(name = "phases", aliases = { "lp", "ps" }, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "List available update phases.")
	public static class ListPhases implements Callable<Integer> {

		
		@ParentCommand
		private Update parent;
		
		@Spec
		CommandSpec spec;

		@Override
		public Integer call() throws Exception {
			var cli = parent.parent.cli();
			var app = cli.tty().getTTYContext().getContainer();
			var updateService = app.getUpdateService();
			for(var p : updateService.getPhases()) {
				cli.print(p.name());
			}
			return 0;
		}
	}

}
