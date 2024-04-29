package com.sshtools.pretty.pricli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jline.builtins.Options.HelpException;
import org.jline.console.ArgDesc;
import org.jline.console.CmdDesc;
import org.jline.console.CommandRegistry;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.SystemCompleter;
import org.jline.utils.AttributedString;

import picocli.CommandLine;
import picocli.CommandLine.Help;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

/**
 * Compiles SystemCompleter for command completion and implements a method
 * commandDescription() that provides command descriptions for JLine
 * TailTipWidgets to be displayed in terminal status bar. SystemCompleter
 * implements the JLine 3 {@link Completer} interface. SystemCompleter generates
 * completion candidates for the specified command line based on the
 * {@link CommandSpec} that this {@code PicocliCommands} was constructed with.
 *
 * @since 4.1.2
 */
public final class PicocliCommandRegistry implements CommandRegistry {

	private class PicocliCompleter extends ArgumentCompleter implements Completer {

		public PicocliCompleter() {
			super(NullCompleter.INSTANCE);
		}

		@Override
		public void complete(LineReader reader, ParsedLine commandLine, List<Candidate> candidates) {
			assert commandLine != null;
			assert candidates != null;
			var word = commandLine.word();
			var words = commandLine.words();
			var sub = findSubcommandLine(words, commandLine.wordIndex());
			if (sub == null) {
				return;
			}
			var cmd = sub.getCommand();
			
			if(cmd instanceof PricliCompleter pce) {
				
				var parsed = sub.parseArgs(wordsToArgs(words));
				var args = parsed.matchedArgs();
//				if(args.isEmpty())
//					return;
				
				var um = parsed.unmatched();
				var pos = parsed.matchedPositionals();
				var lastArg = args.isEmpty() ? null : args.get(args.size() - 1);
				pce.complete(reader, commandLine, candidates, parsed, lastArg);
				System.out.println("Wrds: " + words);
				System.out.println("Args: " + args);
				System.out.println("Um: " + um);
				System.out.println("Pos: " + pos);
			}
			
			else if (word.startsWith("-")) {
				String buffer = word.substring(0, commandLine.wordCursor());
				int eq = buffer.indexOf('=');
				for (OptionSpec option : sub.getCommandSpec().options()) {
					if (option.hidden())
						continue;
					if (option.arity().max() == 0 && eq < 0) {
						addCandidates(candidates, Arrays.asList(option.names()));
					} else {
						if (eq > 0) {
							String opt = buffer.substring(0, eq);
							if (Arrays.asList(option.names()).contains(opt) && option.completionCandidates() != null) {
								addCandidates(candidates, option.completionCandidates(), buffer.substring(0, eq + 1),
										"", true);
							}
						} else {
							addCandidates(candidates, Arrays.asList(option.names()), "", "=", false);
						}
					}
				}
			} else {
				addCandidates(candidates, sub.getSubcommands().keySet());
				for (CommandLine s : sub.getSubcommands().values()) {
					if (!s.getCommandSpec().usageMessage().hidden()) {
						addCandidates(candidates, Arrays.asList(s.getCommandSpec().aliases()));
					}
				}
			}
		}

		private String[] wordsToArgs(List<String> words) {
			var nl = new ArrayList<String>(words);
			nl.remove(0);
			if(!nl.isEmpty() && nl.get(nl.size() - 1).equals("")) {
				nl.remove(nl.size() - 1);
			}
			return nl.toArray(new String[0]);
		}

		private void addCandidates(List<Candidate> candidates, Iterable<String> cands) {
			addCandidates(candidates, cands, "", "", true);
		}

		private void addCandidates(List<Candidate> candidates, Iterable<String> cands, String preFix, String postFix,
				boolean complete) {
			for (String s : cands) {
				candidates.add(new Candidate(AttributedString.stripAnsi(preFix + s + postFix), s, null, null, null,
						null, complete));
			}
		}

	}

	private String picocliCommandsName;
	private final CommandLine cmd;
	private final Set<String> commands;
	private final Map<String, String> aliasCommand = new HashMap<>();

	public PicocliCommandRegistry(CommandLine cmd) {
		this.cmd = cmd;
		var subcommands = cmd.getCommandSpec().subcommands().keySet();
		commands = new HashSet<>(subcommands);
		for (String c : subcommands) {
			for (String a : cmd.getSubcommands().get(c).getCommandSpec().aliases()) {
				aliasCommand.put(a, c);
				commands.remove(a);
			}
		}
	}

	@Override
	public Map<String, String> commandAliases() {
		return aliasCommand;
	}

	/**
	 *
	 * @param args
	 * @return command description for JLine TailTipWidgets to be displayed in
	 *         terminal status bar.
	 */
	@Override
	public CmdDesc commandDescription(List<String> args) {
		CommandLine sub = findSubcommandLine(args, args.size());
		if (sub == null) {
			return null;
		}
		CommandSpec spec = sub.getCommandSpec();
		Help cmdhelp = new picocli.CommandLine.Help(spec);
		List<AttributedString> main = new ArrayList<>();
		Map<String, List<AttributedString>> options = new HashMap<>();
		String synopsis = AttributedString
				.stripAnsi(spec.usageMessage().sectionMap().get("synopsis").render(cmdhelp).toString());
		main.add(HelpException.highlightSyntax(synopsis.trim(), HelpException.defaultStyle()));
		// using JLine help highlight because the statement below does not work well...
		// main.add(new
		// AttributedString(spec.usageMessage().sectionMap().get("synopsis").render(cmdhelp).toString()));
		for (OptionSpec o : spec.options()) {
			String key = Arrays.stream(o.names()).collect(Collectors.joining(" "));
			List<AttributedString> val = new ArrayList<>();
			for (String d : o.description()) {
				val.add(new AttributedString(d));
			}
			if (o.arity().max() > 0) {
				key += "=" + o.paramLabel();
			}
			options.put(key, val);
		}
		return new CmdDesc(main, ArgDesc.doArgNames(Arrays.asList("")), options);
	}

	@Override
	public List<String> commandInfo(String command) {
		List<String> out = new ArrayList<>();
		CommandSpec spec = cmd.getSubcommands().get(command).getCommandSpec();
		Help cmdhelp = new picocli.CommandLine.Help(spec);
		String description = AttributedString
				.stripAnsi(spec.usageMessage().sectionMap().get("description").render(cmdhelp).toString());
		out.addAll(Arrays.asList(description.split("\\r?\\n")));
		return out;
	}

	@Override
	public Set<String> commandNames() {
		return commands;
	}

	public SystemCompleter compileCompleters() {
		SystemCompleter out = new SystemCompleter();
		List<String> all = new ArrayList<>();
		all.addAll(commands);
		all.addAll(aliasCommand.keySet());
		out.add(all, new PicocliCompleter());
		return out;
	}

	/**
	 *
	 * @param command
	 * @return true if PicocliCommands contains command
	 */
	public boolean hasCommand(String command) {
		return commands.contains(command) || aliasCommand.containsKey(command);
	}

	@Override
	public Object invoke(CommandRegistry.CommandSession session, String command, Object... args) throws Exception {
		var arguments = new ArrayList<String>();
		arguments.add(command);
		arguments.addAll(Arrays.stream(args).map(Object::toString).toList());
		return cmd.execute(arguments.toArray(new String[0]));
	}

	/**
	 * Returns the name shown for this collection of picocli commands in the usage
	 * help message. If not set with {@link #name(String)}, this returns
	 * {@link CommandRegistry#name()}.
	 * 
	 * @return the name shown for this collection of picocli commands in the usage
	 *         help message
	 */
	@Override
	public String name() {
		if (picocliCommandsName != null) {
			return picocliCommandsName;
		}
		return CommandRegistry.super.name();
	}

	/**
	 * Sets the name shown for this collection of picocli commands in the usage help
	 * message.
	 * 
	 * @param newName the new name to show
	 */
	public void name(String newName) {
		picocliCommandsName = newName;
	}

	private CommandLine findSubcommandLine(CommandLine cmdline, String command) {
		for (CommandLine s : cmdline.getSubcommands().values()) {
			if (s.getCommandName().equals(command) || Arrays.asList(s.getCommandSpec().aliases()).contains(command)) {
				return s;
			}
		}
		return null;
	}

	private CommandLine findSubcommandLine(List<String> args, int lastIdx) {
		CommandLine out = cmd;
		for (int i = 0; i < lastIdx; i++) {
			if (!args.get(i).startsWith("-")) {
				/*
				CommandLine nout = findSubcommandLine(out, args.get(i));
				if (nout == null) {
					break;
				}
				else
					out = nout;
					*/
				out = findSubcommandLine(out, args.get(i));
				if (out == null) {
					break;
				}
			}
		}
		return out;
	}
}