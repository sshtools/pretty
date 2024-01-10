package com.sshtools.pretty.pricli.ssh;

import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import com.sshtools.common.events.EventCodes;
import com.sshtools.common.logger.Log;
import com.sshtools.common.logger.Log.Level;
import com.sshtools.common.sftp.SftpStatusException;
import com.sshtools.common.ssh.SecurityLevel;
import com.sshtools.common.ssh.SshException;
import com.sshtools.pretty.pricli.ConnectionCommands;
import com.sshtools.pretty.pricli.Styling;
import com.sshtools.pretty.ssh.SshConnector;
import com.sshtools.pretty.ssh.SshInstance;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command
public abstract class AbstractSshCommand implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(AbstractSshCommand.class.getName());

	static {
//		JCEProvider.enableBouncyCastle(true);
	}

	@Option(names = { "-D", "--disable-agent" }, description = "disable ssh-agent authentication")
	boolean disableAgent = false;

	@Option(names = { "-I", "--ignore-identities" }, description = "ignore default identities in .ssh folder")
	boolean ignoreIdentities = false;

	@Option(names = { "-a",
			"--agent" }, paramLabel = "SOCKET", description = "the path to the ssh-agent socket or named pipe")
	Optional<String> agentSocket;

	@Option(names = { "-i",
			"--identity" }, paramLabel = "FILE", description = "the identity file you want to authenticate with")
	Optional<Path> identityFile;

	@Option(names = { "-l", "--log" }, paramLabel = "LEVEL", description = "Log to console (INFO,DEBUG)")
	Optional<Level> maverickConsoleLevel;

	@Option(names = { "-c", "--cipher" }, paramLabel = "CIPHER", description = "Set the preferred cipher (C->S & S->C)")
	Optional<String> cipher;

	@Option(names = { "-z",
			"--compression" }, paramLabel = "TYPE", description = "Set the preferred compression (C->S & S->C)")
	Optional<String> compression;

	@Option(names = { "-s", "--security-level" }, paramLabel = "LEVEL", description = "Set the security level")
	Optional<SecurityLevel> securityLevel;

	@Option(names = { "-k", "--kex" }, paramLabel = "KEX", description = "Set the preferred key exchange")
	Optional<String> kex;

	@Option(names = { "-m", "--mac" }, paramLabel = "MAC", description = "Set the preferred MAC")
	Optional<String> mac;

	@Option(names = { "-x",
			"--public-key" }, paramLabel = "ALGO", description = "Set the preferred public key algorithm")
	Optional<String> publicKeyAlgortithm;

	@ParentCommand
	protected ConnectionCommands parent;

	private SshConnector connector;

	protected SshInstance sshInstance;

	public SshConnector connector() {
		return connector;
	}

	@Override
	public final Integer call() throws Exception {
		processArguments();
		connector = populateConnectorBuilder(createConnectorBuilder()).build();
		initCommand();
		var exitCode = runCommand();
		afterCommand();
		return exitCode;
	}
	
	protected abstract void processArguments() throws Exception;

	protected abstract SshConnector.Builder createConnectorBuilder();

	protected SshConnector.Builder populateConnectorBuilder(SshConnector.Builder builder) {
		var term = parent.cli().jline();
		try {
			builder.withHostKeyVerification(new HostKeyVerificationPopups(parent.ttyContext().getContainer()));
		} catch (SshException e) {
			throw new IllegalStateException("Failed to configure host key verification.", e);
		}
		builder.withDisableAgent(disableAgent);
		builder.withIgnoreIdentities(ignoreIdentities);
		builder.withAgentSocket(agentSocket);
		builder.withIdentityFile(identityFile);
		builder.withCipher(cipher);
		builder.withCompression(compression);
		builder.withPasswordStorage(parent.cli().tty().ttyContext().getContainer().passwords());
		builder.withKex(kex);
		builder.withMac(kex);
		builder.withSecurityLevel(securityLevel);
		builder.withPublicKeyAlgortithm(publicKeyAlgortithm);
		builder.onMessage((msg, args) -> {
			if (args == null)
				parent.cli().print(Styling.styled(msg).toAnsi(term));
			else
				parent.cli().print(Styling.styled(MessageFormat.format(msg, args)).toAnsi(term));
		});
		builder.onPrompt(prompt -> parent.cli().reader().readLine(prompt  + ": ", '●'));
		builder.onError((msg, args) -> {
			if (args == null)
				parent.cli().printError(Styling.styled(msg).toAnsi(term));
			else
				parent.cli().printError(Styling.styled(MessageFormat.format(msg, args)).toAnsi(term));
		});
		return builder;
	}

	public SshInstance instance() {
		return sshInstance;
	}

	public final void initCommand() throws Exception {
		if (this.sshInstance == null) {
			maverickConsoleLevel.ifPresent(l -> Log.enableConsole(Level.DEBUG));
			var term = parent.cli().jline();
			beforeCommand();
			Styling.styled(MessageFormat.format(RESOURCES.getString("connecting"), connector.username(),
					connector.hostname(), connector.port())).println(term);
			var ssh = connector.connect();
			ssh.getConnection().addEventListener(evt -> {
				if (evt.getId() == EventCodes.EVENT_DISCONNECTED) {
					maverickConsoleLevel.ifPresent(l -> {
						if (l.compareTo(Level.INFO) > 0) {
							Styling.styled(MessageFormat.format(RESOURCES.getString("remoteDisconnected"),
									connector.username(), connector.hostname(), connector.port())).println(term);
						}
					});
					
					sshInstance = null;

					/*
					 * TODO: note, this may be called as a result of a shutdown hook, itself the
					 * result of a System.exit(0). It will be ambiguous as to what exit code results
					 * so we can only return a zero here.
					 */
//					System.exit(0);
				}
			});
			
			sshInstance = new SshInstance(connector(), ssh);
		}
	}
	
	protected abstract int runCommand() throws Exception;

	protected void beforeCommand() {
	}

	protected void afterCommand() throws IOException, SshException, SftpStatusException {
	}

}
