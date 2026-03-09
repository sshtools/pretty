package com.sshtools.pretty.ssh;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.sshtools.agent.client.SshAgentClient;
import com.sshtools.agent.exceptions.AgentNotAvailableException;
import com.sshtools.client.IdentityFileAuthenticator;
import com.sshtools.client.PassphrasePrompt;
import com.sshtools.common.logger.Log;
import com.sshtools.common.publickey.InvalidPassphraseException;
import com.sshtools.common.publickey.SignatureGenerator;
import com.sshtools.common.publickey.SshKeyUtils;
import com.sshtools.common.ssh.components.SshPublicKey;
import com.sshtools.common.util.IOUtils;

public class IdentityFileAuthenticatorWithAgentSupport extends IdentityFileAuthenticator implements Closeable {

	private final List<SshPublicKey> agentKeys;
	private final List<SshPublicKey> triedAgentKeys = new ArrayList<>();
	private final SshAgentClient agent;

	public IdentityFileAuthenticatorWithAgentSupport(Collection<Path> identities, PassphrasePrompt passphrase,
			SshAgentClient agent) throws IOException, AgentNotAvailableException {
		super(identities, passphrase);
		this.agent = agent;
		var allAgentKeys = agent.listKeys();
		agentKeys = new ArrayList<>(allAgentKeys.keySet());
		if (Log.isDebugEnabled()) {
			dumpAgentKeys(allAgentKeys);
		}
	}

	public IdentityFileAuthenticatorWithAgentSupport(PassphrasePrompt passphrase, SshAgentClient agent)
			throws IOException, AgentNotAvailableException {
		super(passphrase);
		this.agent = agent;
		var allAgentKeys = agent.listKeys();
		agentKeys = new ArrayList<>(allAgentKeys.keySet());
		if (Log.isDebugEnabled()) {
			dumpAgentKeys(allAgentKeys);
		}
	}

	@Override
	public void close() {
		agent.close();
	}

	protected void dumpAgentKeys(Map<SshPublicKey, String> agentMap) {
		Log.debug("All keys ..");
		
		for (var k : agentKeys) {
			try {
				Log.debug("  [Agent : {}] Public key: {}", agentMap.get(k), SshKeyUtils.getOpenSSHFormattedKey(k));
			} catch (IOException e) {
				throw new IllegalStateException(e.getMessage(), e);
			}
		}
		
		for (var p : getIdentities()) {
			if(Files.exists(p)) {
				try {
					Log.debug("  [Local : {}] Public key: {}", p, IOUtils.readStringFromFile(p, Charset.defaultCharset().name()));
				} catch (IOException e) {
					Log.error("Failed to read public key from identity file {}", p, e);
				}
			}
		}
	}

	@Override
	protected SignatureGenerator getSignatureGenerator() throws IOException, InvalidPassphraseException {
		if (getCurrentKey() != null && triedAgentKeys.contains(getCurrentKey())) {
			return agent;
		} else {
			return super.getSignatureGenerator();
		}
	}

	@Override
	protected boolean hasCredentialsRemaining() {
		while (!agentKeys.isEmpty()) {
			var key = agentKeys.remove(0);
			triedAgentKeys.add(key);
			setCurrentKey(key);
			return true;
		}

		return super.hasCredentialsRemaining();
	}

}
