package com.sshtools.pretty.pricli.telnet;

import static javafx.application.Platform.runLater;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.widget.AutosuggestionWidgets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.pretty.pricli.ConnectionCommands;
import com.sshtools.pretty.pricli.Styling;
import com.sshtools.pretty.telnet.TelnetClient;
import com.sshtools.pretty.telnet.TelnetProtocol;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "telnet",
		aliases = { "tn" },
		footer = "%nAliases: tn",
		usageHelpAutoWidth = true,
		mixinStandardHelpOptions = true,
		description = "Connect to remote hosts via the Telnet protocol.",
		subcommands = {
				Telnet.Connect.class,
				Telnet.Disconnect.class
		})
public class Telnet implements Callable<Integer> {

	static final Logger LOG = LoggerFactory.getLogger(Telnet.class);
	static final ResourceBundle RESOURCES = ResourceBundle.getBundle(Telnet.class.getName());
	private static final AtomicInteger counter = new AtomicInteger(1);

	@Parameters(index = "0", arity = "0..1", paramLabel = "DESTINATION",
			description = "Destination in the form hostname[:port]")
	private Optional<String> destination;

	@ParentCommand
	ConnectionCommands parent;

	Optional<TelnetProtocol> active = Optional.empty();

	@Override
	public Integer call() throws Exception {
		active.ifPresentOrElse(
				a -> Styling.styled(MessageFormat.format(RESOURCES.getString("active"), a.displayName()))
						.println(parent.cli().jline()),
				() -> Styling.styled(RESOURCES.getString("inactive"))
						.println(parent.cli().jline()));
		return 0;
	}

	@Command(name = "connect",
			aliases = { "c" },
			usageHelpAutoWidth = true,
			description = "Connect to a remote host via Telnet.")
	public static final class Connect implements Callable<Integer> {

		@ParentCommand
		private Telnet parent;

		@Parameters(index = "0", arity = "0..1", paramLabel = "DESTINATION",
				description = "Destination in the form hostname[:port]")
		private Optional<String> destination;

		@Option(names = { "-p", "--port" }, paramLabel = "NUMBER",
				description = "The port to connect to on the remote host (default 23).")
		private Optional<Integer> port;

		@Option(names = { "-t", "--terminal-type" }, paramLabel = "TYPE",
				description = "Terminal type to negotiate (default xterm-256color).")
		private Optional<String> terminalType;

		@Option(names = { "--timeout" }, paramLabel = "SECONDS",
				description = "Connection timeout in seconds (default 30).")
		private Optional<Integer> timeout;

		@Option(names = { "-H", "--no-pop" }, paramLabel = "NUMBER",
				description = "Do not automatically return to the terminal on successful connection.")
		private boolean noPop;

		@Option(names = { "--prompt" },
				description = "Prompt for hostname.")
		private boolean prompt;

		@Override
		public Integer call() throws Exception {
			if (parent.active.isPresent()) {
				throw new IllegalStateException(
						MessageFormat.format(RESOURCES.getString("alreadyConnected"), parent.active.get().displayName()));
			}

			if (parent.parent.tty().protocols().size() > 1) {
				throw new IllegalStateException(
						MessageFormat.format(RESOURCES.getString("connectedToOther"), parent.parent.tty().protocol().displayName()));
			}

			var hostname = "localhost";
			var selectedPort = port.orElse(23);

			/* Resolve destination */
			var dest = destination.or(() -> parent.destination);
			if (dest.isPresent()) {
				var destStr = dest.get();
				var idx = destStr.lastIndexOf(':');
				if (idx != -1) {
					hostname = destStr.substring(0, idx);
					if (!port.isPresent()) {
						selectedPort = Integer.parseInt(destStr.substring(idx + 1));
					}
				} else {
					hostname = destStr;
				}
			} else if (prompt) {
				var defVal = "localhost";
				var hostnameHistory = parent.parent.tty().ttyContext().history("telnet-hostname");
				var parentReader = parent.parent.cli().reader();
				var hostnameReader = LineReaderBuilder.builder()
						.history(hostnameHistory.history())
						.terminal(parentReader.getTerminal())
						.variable(LineReader.HISTORY_FILE, hostnameHistory.path())
						.variable(LineReader.HISTORY_SIZE, hostnameHistory.maxSize())
						.build();

				new AutosuggestionWidgets(hostnameReader).enable();

				var input = hostnameReader.readLine(
						Styling.styled(RESOURCES.getString("hostname")).toAnsi(parent.parent.cli().jline()),
						Styling.styled(MessageFormat.format(RESOURCES.getString("hostname.right"), defVal))
								.toAnsi(parent.parent.cli().jline()),
						(Character) null, null);

				if (input == null) {
					throw new IllegalStateException("Cancelled.");
				}
				if (input.isEmpty()) {
					input = defVal;
				}

				var idx = input.lastIndexOf(':');
				if (idx != -1) {
					hostname = input.substring(0, idx);
					if (!port.isPresent()) {
						selectedPort = Integer.parseInt(input.substring(idx + 1));
					}
				} else {
					hostname = input;
				}
			} else {
				throw new IllegalArgumentException(RESOURCES.getString("noPromptOrDestination"));
			}

			var tty = parent.parent.tty();
			var vdu = tty.terminal().getViewport();

			/* Build the telnet client */
			var builder = TelnetClient.builder(hostname)
					.withPort(selectedPort)
					.withWindowSize(vdu.getColumns(), vdu.getRows())
					.withTerminalType(terminalType.orElse(vdu.getTerminalType().getId()));
			timeout.ifPresent(t -> builder.withConnectTimeout(t * 1000));

			var client = builder.build();

			final var host = hostname;
			final var prt = selectedPort;

			try {
				LOG.info("Connecting via telnet to {}:{}", host, prt);
				client.connect();

				var proto = new TelnetProtocol(client);
				parent.active = Optional.of(proto);

				/* Write a message on the terminal viewport */
				vdu.newline();
				vdu.cr();
				vdu.writeString(Styling
						.styled(MessageFormat.format(RESOURCES.getString("connected"),
								prt == 23 ? host : host + ":" + prt))
						.toAnsi());
				vdu.newline();
				vdu.cr();
				vdu.flush();

				if (!noPop) {
					runLater(parent.parent.cli()::close);
				}

				if (parent.parent.cli().isInteractive()) {
					new Thread(() -> {
						tty.protocol(proto);
					}, "Telnet-" + counter.getAndIncrement()).start();
				} else {
					tty.protocol(proto);
				}

				return 0;
			} catch (Exception e) {
				try {
					client.close();
				} catch (Exception e2) {
					// suppress
				}
				parent.active = Optional.empty();
				LOG.error("Telnet connection failed.", e);
				throw e;
			}
		}
	}

	@Command(name = "disconnect",
			aliases = { "d", "dc" },
			usageHelpAutoWidth = true,
			description = "Disconnect from the current telnet session.")
	public static final class Disconnect implements Callable<Integer> {

		@ParentCommand
		private Telnet parent;

		@Override
		public Integer call() throws Exception {
			if (parent.active.isPresent()) {
				var proto = parent.active.get();
				try {
					proto.close();
				} finally {
					parent.active = Optional.empty();
				}
				parent.parent.cli().result(
						Styling.styled(MessageFormat.format(RESOURCES.getString("disconnected"), proto.displayName()))
								.toAttributedString());
			} else {
				throw new IllegalStateException(RESOURCES.getString("notConnected"));
			}
			return 0;
		}
	}
}
