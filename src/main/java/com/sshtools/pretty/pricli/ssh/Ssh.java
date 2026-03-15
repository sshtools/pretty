package com.sshtools.pretty.pricli.ssh;

import static javafx.application.Platform.runLater;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.utils.Log;
import org.jline.widget.AutosuggestionWidgets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.client.tasks.ShellTask;
import com.sshtools.pretty.pricli.Styling;
import com.sshtools.pretty.ssh.SshConnector;
import com.sshtools.pretty.ssh.SshConnector.Builder;
import com.sshtools.pretty.ssh.SshProtocol;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "ssh", usageHelpAutoWidth = true,

		description = "Make SSH connection to remote host.")
public class Ssh extends AbstractSshCommand {

	private final static Logger LOG = LoggerFactory.getLogger(Ssh.class);

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Ssh.class.getName());

	@Option(names = { "-p",
			"--port" }, paramLabel = "NUMBER", description = "The port to connect to on the remote host")
	private Optional<Integer> port;

	@Option(names = { "-H",
			"--no-pop" }, paramLabel = "NUMBER", description = "Do not automatically return to the terminal on successful connection.")
	private boolean noPop;

	@Option(names = { "--prompt" }, description = "Prompt for hostname and/or username.")
	private boolean prompt;

	@Parameters(index = "0", arity = "0..1", paramLabel = "DESTINATION", description = "Destination")
	private Optional<String> destination;

	private String hostname = "localhost";
	private String username = System.getProperty("user.name");
	private int selectedPort;

	@Override
	protected void processArguments() throws Exception {
		if (parent.tty().protocols().size() > 1) {
			throw new IllegalStateException(MessageFormat.format(RESOURCES.getString("connectedToOther"),
					parent.tty().protocol().displayName()));
		}

		if (destination.isPresent()) {
			selectedPort = this.port.orElseGet(() -> {
				var idx = destination.get().lastIndexOf(':');
				if (idx == -1) {
					return 22;
				} else {
					return Integer.parseInt(destination.get().substring(idx + 1));
				}
			});
			hostname = destination.get();
			var idx = hostname.indexOf('@');
			if (idx != -1) {
				username = hostname.substring(0, idx);
				hostname = hostname.substring(idx + 1);
			}
		} else {
			if (prompt) {
				var defVal = "localhost";

				var hostnameHistory = parent.tty().ttyContext().history("hostname");
				var parentReader = parent.cli().reader();
				var hostnameReader = LineReaderBuilder.builder()
						.history(hostnameHistory.history())
						.terminal(parentReader.getTerminal())
						.variable(LineReader.HISTORY_FILE, hostnameHistory.path())
						.variable(LineReader.HISTORY_SIZE, hostnameHistory.maxSize())
						.build();

				new AutosuggestionWidgets(hostnameReader).enable();

				var dest = hostnameReader.readLine(
						Styling.styled(RESOURCES.getString("hostname")).toAnsi(parent.cli().jline()),
						Styling.styled(MessageFormat.format(RESOURCES.getString("hostname.right"), defVal))
								.toAnsi(parent.cli().jline()),
						(Character) null, null);

				if (dest == null) {
					throw new IllegalStateException("Cancelled.");
				} else {
					if (dest.equals("")) {
						dest = defVal;
					}
					var idx = dest.lastIndexOf(':');
					if (idx == -1) {
						hostname = dest;
						selectedPort = 22;
					} else {
						hostname = dest.substring(0, idx);
						selectedPort = Integer.parseInt(dest.substring(idx + 1));
					}

					var defusr = System.getProperty("user.name");
					var usernameHistory = parent.tty().ttyContext().history("usernames");
					var usernameReader = LineReaderBuilder.builder()
							.history(usernameHistory.history())
							.terminal(parentReader.getTerminal())
							.variable(LineReader.HISTORY_FILE, usernameHistory.path())
							.variable(LineReader.HISTORY_SIZE, usernameHistory.maxSize())
							.build();

					new AutosuggestionWidgets(usernameReader).enable();

					username = usernameReader.readLine(RESOURCES.getString("username"),
							Styling.styled(MessageFormat.format(RESOURCES.getString("username.right"), defusr))
									.toAnsi(parent.cli().jline()),
							(Character) null, null);
					if (username == null) {
						throw new IllegalStateException("Cancelled.");
					} else if (username.equals("")) {
						username = defusr;
					}
				}
			} else {
				throw new IllegalArgumentException(RESOURCES.getString("noPromptOrDestination"));
			}
		}
	}

	@Override
	protected Builder createConnectorBuilder() {
		return new SshConnector.Builder(hostname, username, selectedPort);
	}

	@Override
	protected int runCommand() throws Exception {
		var ssh = instance().client();
		var cli = parent.cli();
		if (ssh.isAuthenticated()) {
			cli.result(MessageFormat.format(RESOURCES.getString("authenticated"), username));
		} else {
			cli.result(MessageFormat.format(RESOURCES.getString("authenticationFailed"), username));
			return 2;
		}

		var tty = parent.tty();
		var vdu = tty.terminal().getViewport();
		var env = tty.environment();
		var closed = new AtomicBoolean();

		/* Start shell */
		var tsk = ssh.addTask(ShellTask.ShellTaskBuilder.create()
				.withClient(ssh)
				.withTermType(vdu.getTerminalType().getId())
				.withColumns(vdu.getColumns())
				.withRows(vdu.getRows())
				.onBeforeOpen((task, session) -> {
					env.forEach((k, v) -> session.setEnvironmentVariable(k, v));
				})
				.onClose((task, session) -> {
					if (!closed.get()) {
						// TODO on remote close?
					}

					if (session.getConnectionProtocol().getActiveChannels().isEmpty()) {
						Log.info("Disconnecting, last session closed.");
						ssh.disconnect();
					}
				}).onTask((task, session) -> {

					/* Write out a message on the terminal, not the shell */
					vdu.newline();
					vdu.cr();
					vdu.writeString(Styling
							.styled(MessageFormat.format(RESOURCES.getString("connected"),
									username + "@"
											+ (selectedPort == 22 ? hostname : hostname + ":" + selectedPort)))
							.toAnsi());
					vdu.newline();
					vdu.cr();
					vdu.flush();

					if (!noPop) {
						runLater(cli::close);
					}

					var proto = new SshProtocol(this, instance(), session);
					try {
						tty.protocol(proto);
						LOG.info("SSH protocol exited, returning to terminal.");
					} catch (Exception e) {
						cli.printException(e);
					} finally {
						closed.set(true);
					}
					
				}).build());
		

		if (!cli.isInteractive()) {
			LOG.info("Waiting for SSH task to end.");
			try {
				tsk.waitIndefinitely();
			} finally {
				LOG.info("SSH task ended.");
			}
		}

		return 0;
	}

}
