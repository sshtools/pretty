package com.sshtools.pretty.ssh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.agent.client.SshAgentClient;
import com.sshtools.agent.exceptions.AgentNotAvailableException;
import com.sshtools.client.ExternalKeyAuthenticator;
import com.sshtools.client.IdentityFileAuthenticator;
import com.sshtools.client.PasswordAuthenticator;
import com.sshtools.client.PrivateKeyFileAuthenticator;
import com.sshtools.client.SshClient;
import com.sshtools.client.SshClient.SshClientBuilder;
import com.sshtools.client.SshClientContext;
import com.sshtools.common.knownhosts.HostKeyVerification;
import com.sshtools.common.knownhosts.KnownHostsFile;
import com.sshtools.common.publickey.SshPrivateKeyFileFactory;
import com.sshtools.common.ssh.SecurityLevel;
import com.sshtools.common.ssh.SshException;
import com.sshtools.pretty.SecretStorage;
import com.sshtools.pretty.SecretStorage.Key;
import com.sshtools.synergy.ssh.SshContext;

public class SshConnector {
	static Logger LOG = LoggerFactory.getLogger(SshConnector.class);
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(SshConnector.class.getName());

	public final static class Builder {

		private boolean disableAgent = false;
		private boolean ignoreIdentities = false;
		private Optional<SecurityLevel> securityLevel = Optional.empty();
		private final String hostname;
		private final String username;
		private final int port;
		private Optional<Path> identityFile = Optional.empty();
		private Optional<String> agentSocket = Optional.empty();
		private Optional<String> cipher = Optional.empty();
		private Optional<String> compression = Optional.empty();
		private Optional<String> kex = Optional.empty();
		private Optional<String> mac = Optional.empty();
		private Optional<String> publicKeyAlgortithm = Optional.empty();
		private Optional<BiConsumer<String, Object[]>> onError = Optional.empty();
		private Optional<BiConsumer<String, Object[]>> onMessage = Optional.empty();
		private Optional<Function<String, String>> onPrompt = Optional.empty();
		private long authenticationTimeout = 120;
		private Optional<HostKeyVerification> hostKeyVerification = Optional.empty();
		private Optional<SecretStorage> passwordStorage = Optional.empty();

		public Builder(String hostname, String username, int port) {
			this.hostname = hostname;
			this.username = username;
			this.port = port;
		}

		public Builder onMessage(BiConsumer<String, Object[]> onMessage) {
			this.onMessage = Optional.of(onMessage);
			return this;
		}

		public Builder onError(BiConsumer<String, Object[]> onError) {
			this.onError = Optional.of(onError);
			return this;
		}

		public Builder onPrompt(Function<String, String> onPrompt) {
			this.onPrompt = Optional.of(onPrompt);
			return this;
		}

		public Builder withHostKeyVerification(HostKeyVerification hostKeyVerification) {
			this.hostKeyVerification = Optional.of(hostKeyVerification);
			return this;
		}

		public Builder withAuthenticationTimeout(int authenticationTimeout) {
			this.authenticationTimeout = authenticationTimeout;
			return this;
		}

		public Builder withDisableAgent() {
			return withDisableAgent(true);
		}

		public Builder withDisableAgent(boolean disableAgent) {
			this.disableAgent = disableAgent;
			return this;
		}

		public Builder withIgnoreIdentities() {
			return withIgnoreIdentities(true);
		}

		public Builder withIgnoreIdentities(boolean ignoreIdentities) {
			this.ignoreIdentities = ignoreIdentities;
			return this;
		}

		public Builder withPasswordStorage(SecretStorage passwordStorage) {
			this.passwordStorage = Optional.of(passwordStorage);
			return this;
		}

		public Builder withSecurityLevel(SecurityLevel level) {
			return withSecurityLevel(Optional.of(level));
		}

		public Builder withSecurityLevel(Optional<SecurityLevel> securityLevel) {
			this.securityLevel = securityLevel;
			return this;
		}

		public Builder withIdentityFile(Path identityFile) {
			return withIdentityFile(Optional.of(identityFile));
		}

		public Builder withIdentityFile(Optional<Path> identityFile) {
			this.identityFile = identityFile;
			return this;
		}

		public SshConnector build() {
			return new SshConnector(this);
		}

		public Builder withAgentSocket(String agentSocket) {
			return withAgentSocket(Optional.of(agentSocket));
		}

		public Builder withAgentSocket(Optional<String> agentSocket) {
			this.agentSocket = agentSocket;
			return this;

		}

		public Builder withCipher(String cipher) {
			return withCipher(Optional.of(cipher));

		}

		public Builder withCipher(Optional<String> cipher) {
			this.cipher = cipher;
			return this;

		}

		public Builder withCompression(String compression) {
			return withCompression(Optional.of(compression));
		}

		public Builder withCompression(Optional<String> compression) {
			this.compression = compression;
			return this;
		}

		public Builder withKex(String kex) {
			return withKex(Optional.of(kex));
		}

		public Builder withKex(Optional<String> kex) {
			this.kex = kex;
			return this;

		}

		public Builder withMac(String mac) {
			return withMac(Optional.of(mac));
		}

		public Builder withMac(Optional<String> mac) {
			this.mac = mac;
			return this;
		}

		public Builder withPublicKeyAlgortithm(String publicKeyAlgortithm) {
			return withPublicKeyAlgortithm(Optional.of(publicKeyAlgortithm));
		}

		public Builder withPublicKeyAlgortithm(Optional<String> publicKeyAlgortithm) {
			this.publicKeyAlgortithm = publicKeyAlgortithm;
			return this;
		}
	}

	private final boolean disableAgent;
	private final boolean ignoreIdentities;
	private final Optional<SecurityLevel> securityLevel;
	private final Optional<Path> identityFile;
	private final String hostname;
	private final String username;
	private final int port;
	private final Optional<String> agentSocket;
	private final Optional<String> cipher;
	private final Optional<String> compression;
	private final Optional<String> kex;
	private final Optional<String> mac;
	private final Optional<String> publicKeyAlgortithm;
	private final Optional<BiConsumer<String, Object[]>> onError;
	private final Optional<Function<String, String>> onPrompt;
	private final Optional<BiConsumer<String, Object[]>> onMessage;
	private final long authenticationTimeout;
	private final HostKeyVerification hkv;
	private final Optional<SecretStorage> passwordStorage;

	private SshConnector(Builder bldr) {
		passwordStorage = bldr.passwordStorage;
		authenticationTimeout = bldr.authenticationTimeout;
		disableAgent = bldr.disableAgent;
		ignoreIdentities = bldr.ignoreIdentities;
		securityLevel = bldr.securityLevel;
		hostname = bldr.hostname;
		username = bldr.username;
		port = bldr.port;
		identityFile = bldr.identityFile;
		agentSocket = bldr.agentSocket;
		cipher = bldr.cipher;
		compression = bldr.compression;
		kex = bldr.kex;
		mac = bldr.mac;
		publicKeyAlgortithm = bldr.publicKeyAlgortithm;
		onError = bldr.onError;
		onPrompt = bldr.onPrompt;
		onMessage = bldr.onMessage;
		hkv = bldr.hostKeyVerification.orElseGet(() -> {
			try {
				return new KnownHostsFile();
			} catch (SshException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	public String hostname() {
		return hostname;
	}

	public String username() {
		return username;
	}

	public int port() {
		return port;
	}

	public SshClient connect() throws IOException, SshException {
		return connect(false, disableAgent, ignoreIdentities, identityFile.orElse(null), true);
	}

	public SshClient connect(boolean ignorePassword, boolean verbose) throws IOException, SshException {
		return connect(ignorePassword, disableAgent, ignoreIdentities, identityFile.orElse(null), verbose);
	}

	public SshClient connect(boolean ignorePassword, boolean disableAgent, boolean ignoreIdentities, Path identityFile,
			boolean verbose) throws IOException, SshException {

		var sshContext = new SshClientContext(securityLevel.orElse(SecurityLevel.WEAK));
//		var term = parent.cli().jline();

		if (securityLevel.isPresent()) {
			if (!cipher.isEmpty()) {
				sshContext.setPreferredCipherCS(cipher.get());
				sshContext.setPreferredCipherSC(cipher.get());
			}
		} else {
			if (cipher.isEmpty()) {
				sshContext.setPreferredCipherCS(SshContext.CIPHER_AES256_CTR);
				sshContext.setPreferredCipherSC(SshContext.CIPHER_AES256_CTR);
			} else {
				sshContext.setPreferredCipherCS(cipher.get());
				sshContext.setPreferredCipherSC(cipher.get());
			}
		}
		sshContext.setPreferredCompressionCS(compression.orElse("none"));
		sshContext.setPreferredCompressionSC(compression.orElse("none"));
		if (kex.isPresent())
			sshContext.setPreferredKeyExchange(kex.get());
		if (mac.isPresent()) {
			sshContext.setPreferredMacCS(mac.get());
			sshContext.setPreferredMacSC(mac.get());
		}
		if (publicKeyAlgortithm.isPresent()) {
			sshContext.setPreferredPublicKey(publicKeyAlgortithm.get());
		}
		sshContext.setHostKeyVerification(hkv);

		var bldr = SshClientBuilder.create().withTarget(hostname, port).withUsername(username)
				.withSshContext(sshContext);
		var ssh = bldr.build();
		
		if (Objects.nonNull(identityFile)) {
			/* An explicit identity file is specified, so try authenticating with it. */
			if (!Files.exists(identityFile)) {
				if (verbose)
					onError.ifPresent(c -> c.accept(RESOURCES.getString("identityFileDoesntExist"), null));
			} else {
				var file = SshPrivateKeyFileFactory.parse(identityFile);
				String passphrase = null;
				var storageServiceName = Base64.getEncoder().encodeToString(file.getFormattedKey());
				var key = new Key("ssh", username, storageServiceName);

				if (file.isPassphraseProtected()) {
					passphrase = getCachedSecretOrPrompt(key, RESOURCES.getString("passphrase"));
				}

				if (!ssh.isConnected()) {
					ssh = bldr.build();
				}

				if (!ssh.authenticate(new PrivateKeyFileAuthenticator(identityFile, passphrase),
						TimeUnit.SECONDS.toMillis(authenticationTimeout))) {
					passwordStorage.ifPresent(ps -> ps.reset(key));
					if (verbose)
						onError.ifPresent(c -> c.accept("identityFileCouldNotAuthenticate", null));
				}
			}
		}
		
		if (!ssh.isAuthenticated()) {
			if (!ssh.isConnected()) {
				ssh = bldr.build();
			}
			
			if(disableAgent && !ignoreIdentities) {
				/* Agent is disabled, but identities are not ignored, so try authenticating with identities. */
				var buf = new StringBuffer();
				var authenticator = new IdentityFileAuthenticator((keyinfo) -> {
					return getIdentityPrompt(verbose, buf, keyinfo);
				});
				if (!ssh.authenticate(authenticator, TimeUnit.SECONDS.toMillis(authenticationTimeout))) {
					passwordStorage.ifPresent(ps -> ps.reset(new Key("ssh", username, buf.toString())));
				}
			}
			else if(!disableAgent) {
				try {
					try (var agent = SshAgentClient.connectOpenSSHAgent("Pretty", agentSocket.orElseGet(this::getDefaultAgentSocket))) {
						
						if(ignoreIdentities) {
							/* Only use agent */
							if (ssh.authenticate(new ExternalKeyAuthenticator(agent),
									TimeUnit.SECONDS.toMillis(authenticationTimeout))) {
								if (verbose)
									onMessage.ifPresent(c -> c.accept(RESOURCES.getString("authenticatedByAgent"), null));
							}
						} else {
							/* Use agent first, then identities */
							var buf = new StringBuffer();
							var authenticator = new IdentityFileAuthenticatorWithAgentSupport((keyinfo) -> {
								return getIdentityPrompt(verbose, buf, keyinfo);
							}, agent);
							if (ssh.authenticate(authenticator, TimeUnit.SECONDS.toMillis(authenticationTimeout))) {
								if (verbose)
									onMessage.ifPresent(c -> c.accept(RESOURCES.getString("authenticatedByAgent"), null));
							}
							else {
								passwordStorage.ifPresent(ps -> ps.reset(new Key("ssh", username, buf.toString())));
							}
						}
						
					} catch (IOException e) {
						if (agentSocket.isPresent() && verbose) {
							onError.ifPresent(c -> c.accept(RESOURCES.getString("failedToConnectToSshAgent"), null));
						}
					} catch (AgentNotAvailableException e) {
						// Purposely ignored
					}
					
					
				} catch (IllegalStateException e) {
					if (verbose)
						onError.ifPresent(c -> c.accept(RESOURCES.getString("noAgent"), null));
				}
			}
		}


		if (!ignorePassword) {
			if (!ssh.isAuthenticated()) {
				if (ssh.getAuthenticationMethods().contains("password")
						|| ssh.getAuthenticationMethods().contains("keyboard-interactive")) {

					if (!ssh.isConnected()) {
						ssh = bldr.build();
					}
					var key = new Key("ssh", username, hostname + (port == 22 ? "" : ":" + port));

					for (int i = 0; i < 3 && ssh.isConnected() && !ssh.isAuthenticated(); i++) {
						LOG.info("Attempting password authentication {} for {}.", i, username);
						if (!ssh.authenticate(PasswordAuthenticator.of(() -> {
							var cachedSecretOrPrompt = getCachedSecretOrPrompt(key, RESOURCES.getString("password"));
							LOG.info("Trying with {} for {}.", cachedSecretOrPrompt, username);
							return cachedSecretOrPrompt;
						}), TimeUnit.SECONDS.toMillis(authenticationTimeout))) {
							LOG.info("Failed, resetting key {}.", key);
							passwordStorage.ifPresent(ps -> ps.reset(key));
							break;
						}
					}
				} else {
					if (verbose)
						onError.ifPresent(c -> c.accept(RESOURCES.getString("passwordNotSupported"), null));
				}
			}
		}

		if (!ssh.isConnected()) {
			throw new IOException("The connection could not be established!");
		}

		if (!ssh.isAuthenticated()) {
			throw new IOException("The connection could not be authenticated!");
		}

		return ssh;
	}

	protected String getIdentityPrompt(boolean verbose, StringBuffer buf, String keyinfo) {
		if (verbose)
			onMessage.ifPresent(c -> c.accept(RESOURCES.getString("acceptableKey"), new Object[] { keyinfo }));
		var storageServiceName = keyinfo + "@" + hostname + (port == 22 ? "" : ":" + port);
		var tmp = getCachedSecretOrPrompt(new Key("ssh", username, storageServiceName), RESOURCES.getString("passphrase"));
		buf.setLength(0);
		buf.append(storageServiceName);
		return tmp;
	}

	private String getDefaultAgentSocket() {
		try {
			return SshAgentClient.getEnvironmentSocket();
		} catch (AgentNotAvailableException e) {
			throw new IllegalStateException(e);
		}
	}

	private String getCachedSecretOrPrompt(Key key, String prompt) {
		if(!passwordStorage.isEmpty()) {
			var ps = passwordStorage.get();
			var pw = ps.secret(key, () -> {
				var str = onPrompt.orElseThrow(() -> new IllegalStateException("No prompt callback set.")).apply(prompt);
				return str == null ? null : str.toCharArray(); 
			});
			if(pw != null) {
				return new String(pw);
			} 
		}
		return onPrompt.orElseThrow(() -> new IllegalStateException("No prompt callback set.")).apply(prompt);
	}

}
