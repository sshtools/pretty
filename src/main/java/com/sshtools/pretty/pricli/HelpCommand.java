package com.sshtools.pretty.pricli;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Map;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help;
import picocli.CommandLine.IHelpCommandInitializable2;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;

@Command(name = "help", aliases = {
		"h" }, header = "Display help information about the specified command.", synopsisHeading = "%nUsage: ", helpCommand = true, description = {
				"%nWhen no COMMAND is given, the usage help for the main command is displayed.",
				"If a COMMAND is specified, the help for that command is shown.%n" })
public final class HelpCommand implements IHelpCommandInitializable2, Runnable {

	@Option(names = { "-h",
			"--help" }, usageHelp = true, descriptionKey = "helpCommand.help", description = "Show usage help for the help command and exit.")
	private boolean helpRequested;

	@Parameters(paramLabel = "COMMAND", arity = "0..1", descriptionKey = "helpCommand.command", description = "The COMMAND to display the usage help message for.")
	private String commands;

	private CommandLine self;
	private PrintWriter out;
	private PrintWriter err;
	private Help.Ansi ansi; // for backwards compatibility with pre-4.0
	private Help.ColorScheme colorScheme;

	/**
	 * Invokes {@link #usage(PrintStream, Help.ColorScheme) usage} for the specified
	 * command, or for the parent command.
	 */
	public void run() {
		CommandLine parent = self == null ? null : self.getParent();
		if (parent == null) {
			return;
		}
		Help.ColorScheme colors = colorScheme != null ? colorScheme : Help.defaultColorScheme(ansi);
		if (commands != null) {
			Map<String, CommandLine> parentSubcommands = parent.getCommandSpec().subcommands();
			CommandLine subcommand = parentSubcommands.get(commands);
			if (subcommand == null && parent.isAbbreviatedSubcommandsAllowed()) {
				// TODO
//				subcommand = AbbreviationMatcher
//						.match(parentSubcommands, commands, parent.isSubcommandsCaseInsensitive(), self).getValue();
			}
			if (subcommand != null) {
				subcommand.usage(out, colors); // for compatibility with pre-4.0 clients
			} else {
				throw new ParameterException(parent, "Unknown subcommand '" + commands + "'.", null, commands);
			}
		} else {
			parent.usage(out, colors); // for compatibility with pre-4.0 clients
		}
	}

	/** {@inheritDoc} */
	public void init(CommandLine helpCommandLine, Help.ColorScheme colorScheme, PrintWriter out, PrintWriter err) {
		this.self = helpCommandLine;
		this.colorScheme = colorScheme;
		this.out = out;
		this.err = err;
	}
}
