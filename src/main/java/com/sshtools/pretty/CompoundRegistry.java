package com.sshtools.pretty;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.jline.console.CmdDesc;
import org.jline.console.CommandRegistry;
import org.jline.reader.impl.completer.SystemCompleter;

@Deprecated
public class CompoundRegistry implements CommandRegistry {
	
	private List<CommandRegistry> subregistries = new CopyOnWriteArrayList<>();
	private SystemCompleter completer;
	
	public void add(CommandRegistry registry) {
		this.subregistries.add(registry);
	}
	
	public void remove(CommandRegistry registry) {
		this.subregistries.remove(registry);
	}

	@Override
	public Object invoke(CommandSession session, String command, Object... args) throws Exception {
		for(var r : subregistries) {
			if(r.hasCommand(command)) {
				return r.invoke(session, command, args);
			}
		}
		return CommandRegistry.super.invoke(session, command, args);
	}

	@Override
	public Set<String> commandNames() {
		return subregistries.stream().map(r -> r.commandNames()).flatMap(Set::stream).collect(Collectors.toSet());
	}

	@Override
	public Map<String, String> commandAliases() {
		return subregistries.stream().map(r -> r.commandAliases()).flatMap(f -> f.entrySet().stream())
				.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
	}

	@Override
	public List<String> commandInfo(String command) {
		return subregistries.stream().map(r -> r.commandInfo(command)).flatMap(List::stream).collect(Collectors.toList());
	}

	@Override
	public boolean hasCommand(String command) {
		for(var r : subregistries)
			if(r.hasCommand(command))
				return true;
		return false;
	}

	@Override
	public SystemCompleter compileCompleters() {
		return CommandRegistry.aggregateCompleters(subregistries.toArray(new CommandRegistry[0]));
	}

	@Override
	public CmdDesc commandDescription(List<String> args) {
		for(var r : subregistries) {
			var desc = r.commandDescription(args);
			if(desc.isValid())
				return desc;
		}
		return new CmdDesc(false);
	}
	
	void checkCompleter() {
		if(completer == null) {
			completer  = new SystemCompleter();
			for(var c : subregistries) {
				completer.add(c.compileCompleters());
			}
		}
	}

}
