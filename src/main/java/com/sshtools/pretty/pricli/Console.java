package com.sshtools.pretty.pricli;

import static javafx.application.Platform.runLater;

import java.util.Optional;
import java.util.concurrent.Callable;

import com.sshtools.pretty.ConsoleProtocol;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "console", 
		 aliases = { "co" }, 
		 footer = "%nAliases: co",
		 usageHelpAutoWidth = true, 
		 mixinStandardHelpOptions = true, 
		 description = "Connect to a native shell.")
public final class Console implements Callable<Integer> {

	@ParentCommand
	private ConnectionCommands parent;

	@Option(names = { "-H",
			"--no-pop" }, paramLabel = "NUMBER", description = "Do not automatically return to the terminal on successful connection.")
	private boolean noPop;

	@Parameters(index = "0", arity = "0..1", paramLabel = "SHELL", description = "The command to run as the shell.")
	private Optional<String> shell;

	@Parameters(index = "1", arity = "0..1", paramLabel = "ARGUMENTS", description = "Arguments to pass to the shell command.")
	private String[] args;

	@Override
	public Integer call() throws Exception {
		var bldr = new ConsoleProtocol.Builder();
		shell.ifPresent(shl ->  { 
			bldr.withCommand(shl);
			bldr.withArguments(args);
		});
		var proto = bldr.build();
		try {
			if (!noPop) {
				runLater(parent.cli()::hide);
			}

			new Thread(() -> {
				parent.tty().protocol(proto);
			}, "InitialProtocol").start();
		} catch (Exception e) {
			try {
				proto.close();
			} catch (Exception e2) {
			}
			throw e;
		}

		return 0;
	}

}