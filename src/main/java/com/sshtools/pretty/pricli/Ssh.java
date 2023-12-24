package com.sshtools.pretty.pricli;

import static javafx.application.Platform.runLater;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sshtools.client.ClientAuthenticator;
import com.sshtools.client.PasswordAuthenticator;
import com.sshtools.client.SshClient;
import com.sshtools.client.tasks.ShellTask;
import com.sshtools.pretty.SshProtocol;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "ssh", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Make SSH connection to remote host.")
public class Ssh implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Ssh.class.getName());
	
	@Option(names = {"-p", "--port"}, paramLabel = "NUMBER", description="The port to connect to on the remote host")
	private Optional<Integer> port;
	
	@Option(names = {"-H", "--no-pop"}, paramLabel = "NUMBER", description="Do not automatically return to the terminal on successful connection.")
	private boolean noPop;
	
	@Parameters(index = "0", arity="1", paramLabel="DESTINATION", description = "Destination")
	private String destination;
	
	@ParentCommand
	private Pricli.PricliCommands parent;
	
	@Override
	public Integer call() throws Exception {
		
		var port = this.port.orElseGet(() -> {
			var idx = destination.lastIndexOf(':');
			if(idx == -1) {
				return 22;
			}
			else {
				return Integer.parseInt(destination.substring(idx + 1));
			}
		});
		var hostname = destination;
		var username = System.getProperty("user.name");
		var idx = hostname.indexOf('@');
		if(idx != -1) {
			username = hostname.substring(0, idx);
			hostname = hostname.substring(idx + 1);
		}

		var rdr = parent.cli().reader();
		
		var ssh = SshClient.SshClientBuilder.create().
				withHostname(hostname).
				withPort(port).
				withUsername(username).
				build();
		
		/* Authenticate */
		ClientAuthenticator auth;
		do {
			auth = new PasswordAuthenticator(rdr.readLine("Password: ", '*'));
			if (ssh.authenticate(auth, 30000)) {
				break;
			}
		} while (ssh.isConnected());
		
		if(ssh.isAuthenticated()) {
			parent.cli().result(MessageFormat.format(RESOURCES.getString("authenticated"), username));
		}
		else {
			parent.cli().result(MessageFormat.format(RESOURCES.getString("authenticationFailed"), username));
			return 2;
		}
		
		var tty = parent.tty();
		var vdu = tty.terminal().getViewport();
		var env = tty.environment();
		var closed = new AtomicBoolean();
		
		/* Start shell */
		ssh.addTask(ShellTask.ShellTaskBuilder.create().
			withClient(ssh).
			withTermType(vdu.getTerminalType().getId()).
			withColumns(vdu.getColumns()).
			withRows(vdu.getRows()).
			onBeforeOpen((task,session) -> {
				env.forEach((k,v)->session.setEnvironmentVariable(k, v));
			}).
			onClose((task, session) -> {
				if(!closed.get()) {
					// TODO on remote close?
//					tty.popProtocol();
				}
			}).
			onTask((task, session) -> {
				
				/* Write out a message on the terminal, not the shell */
				vdu.newline();
				vdu.cr();
				vdu.writeString(Styling.styled(MessageFormat.format(RESOURCES.getString("connected"), destination)).toAnsi());
				vdu.newline();
				vdu.cr();
				vdu.flush();
				
				if(!noPop) {
					runLater(parent.cli()::hide);
				}
				
				var proto = new SshProtocol(session);
				try {
					tty.protocol(proto);
				}
				catch(Exception e) {
					parent.cli().printException(e);
				}
				finally {
					closed.set(true);
				}
			}).
			build());
		
		return 0;
	}

}
