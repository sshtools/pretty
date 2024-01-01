package com.sshtools.pretty.pricli.ssh;

import static javafx.application.Platform.runLater;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sshtools.client.tasks.ShellTask;
import com.sshtools.pretty.pricli.PicocliCommandRegistry;
import com.sshtools.pretty.pricli.PricliCommands;
import com.sshtools.pretty.pricli.Styling;
import com.sshtools.pretty.ssh.SshConnector;
import com.sshtools.pretty.ssh.SshConnector.Builder;
import com.sshtools.pretty.ssh.SshProtocol;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "ssh", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Make SSH connection to remote host.")
public class Ssh extends AbstractSshCommand {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Ssh.class.getName());

	@Option(names = { "-p",
			"--port" }, paramLabel = "NUMBER", description = "The port to connect to on the remote host")
	private Optional<Integer> port;

	@Option(names = { "-H",
			"--no-pop" }, paramLabel = "NUMBER", description = "Do not automatically return to the terminal on successful connection.")
	private boolean noPop;

	@Parameters(index = "0", arity = "1", paramLabel = "DESTINATION", description = "Destination")
	private String destination;

	@ParentCommand
	private PricliCommands parent;

	private String hostname = "localhost";
	private String username = System.getProperty("user.name");
	private int selectedPort;
	private PicocliCommandRegistry registry;

	@Override
	protected void processArguments() throws Exception {
		if(parent.tty().protocols().size() > 1) {
			throw new IllegalStateException(MessageFormat.format(RESOURCES.getString("connectedToOther"), parent.tty().protocol().displayName()));
		}
		
		selectedPort = this.port.orElseGet(() -> {
			var idx = destination.lastIndexOf(':');
			if (idx == -1) {
				return 22;
			} else {
				return Integer.parseInt(destination.substring(idx + 1));
			}
		});
		hostname = destination;
		var idx = hostname.indexOf('@');
		if (idx != -1) {
			username = hostname.substring(0, idx);
			hostname = hostname.substring(idx + 1);
		}
	}

	@Override
	protected Builder createConnectorBuilder() {
		return new SshConnector.Builder(hostname, username, selectedPort);
	}

	@Override
	protected int runCommand() throws Exception {
		var ssh = instance().client();
		if (ssh.isAuthenticated()) {
			parent.cli().result(MessageFormat.format(RESOURCES.getString("authenticated"), username));
		} else {
			parent.cli().result(MessageFormat.format(RESOURCES.getString("authenticationFailed"), username));
			return 2;
		}

		registerCommands();
		
		var tty = parent.tty();
		var vdu = tty.terminal().getViewport();
		var env = tty.environment();
		var closed = new AtomicBoolean();

		/* Start shell */
		ssh.addTask(ShellTask.ShellTaskBuilder.create().withClient(ssh).withTermType(vdu.getTerminalType().getId())
				.withColumns(vdu.getColumns()).withRows(vdu.getRows()).onBeforeOpen((task, session) -> {
					env.forEach((k, v) -> session.setEnvironmentVariable(k, v));
				}).onClose((task, session) -> {
					if (!closed.get()) {
						// TODO on remote close?
//					tty.popProtocol();
					}
				}).onTask((task, session) -> {

					/* Write out a message on the terminal, not the shell */
					vdu.newline();
					vdu.cr();
					vdu.writeString(Styling.styled(MessageFormat.format(RESOURCES.getString("connected"), destination))
							.toAnsi());
					vdu.newline();
					vdu.cr();
					vdu.flush();

					if (!noPop) {
						runLater(parent.cli()::hide);
					}

					var proto = new SshProtocol(instance(), session);
					try {
						tty.protocol(proto);
					} catch (Exception e) {
						parent.cli().printException(e);
					} finally {
						closed.set(true);
						deregisterCommands();
					}
				}).build());

		return 0;
	}
	
	private void deregisterCommands() {
		parent.cli().removeCommandRegistry(registry);
		parent.cli().commandLine().addSubcommand(this);
	}
	
	private void registerCommands() {
		var cmd = parent.cli().newCommand(new SshCommands(this, parent.cli()));
		registry = new PicocliCommandRegistry(cmd);
		registry.name(RESOURCES.getString("ssh"));
		parent.cli().addCommandRegistry(registry);
	}

}
