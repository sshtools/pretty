package com.sshtools.pretty.pricli.ssh;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import com.sshtools.common.knownhosts.KnownHostsFile;
import com.sshtools.common.ssh.SshException;
import com.sshtools.common.ssh.components.SshPublicKey;
import com.sshtools.pretty.AppContext;

import uk.co.bithatch.nativeimage.annotations.Bundle;

@Bundle
public class HostKeyVerificationPopups extends KnownHostsFile {

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(HostKeyVerificationPopups.class.getName());
	
	private AppContext app;
	
	public static  KnownHostsFile create(AppContext terminal) throws SshException, IOException {
		var file = KnownHostsFile.defaultKnownHostsFile() ;
		if(!Files.exists(file))
			Files.createFile(file);
		return new HostKeyVerificationPopups(terminal);
	}

	HostKeyVerificationPopups(AppContext app) throws SshException {
		super();
		this.app = app;

		setHashHosts(true);
		setUseCanonicalHostnames(true);
		setUseReverseDNS(false);
	}

	
	@Override
	protected void onUnknownHost(String host, SshPublicKey key) throws SshException {
		try {
			unknownHost(String.join(" ", resolveNames(host)), key).ifPresent(always -> {
				try {
					allowHost(host, key, always);
				} catch (SshException e) {
					throw new IllegalStateException("Failed to save known host.");
				}
			});
		} catch (UnknownHostException e) {
			throw new SshException(e);
		} 
	}

	@Override
	protected void onHostKeyMismatch(String host, List<SshPublicKey> allowedHostKey, SshPublicKey actualHostKey)
			throws SshException {
		try {
			if(mismatchedHost(String.join(" ", resolveNames(host)), allowedHostKey, actualHostKey)) {
				try {
					for(var key : allowedHostKey) {
						removeEntries(key);
					}
					allowHost(host, actualHostKey, true);
				} catch (SshException e) {
					throw new IllegalStateException("Failed to save known host.");
				}
			}
		} catch (UnknownHostException e) {
			throw new SshException(e);
		} 
	}


	private boolean mismatchedHost(String host, List<SshPublicKey> allowedHostKey, SshPublicKey actualHostKey)
			throws SshException, UnknownHostException {
		
		var others = String.join("\n", allowedHostKey.stream().map(t -> {
			try {
				return t.getFingerprint();
			} catch (SshException e) {
				throw new IllegalStateException(e);
			}
		}).collect(Collectors.toList()));
		
//		do {
//			var fp = actualHostKey.getFingerprint();
//			var answer = terminal.prompt(RESOURCES.getString("mismatchedHost.content"), host, actualHostKey.getJCEPublicKey().getAlgorithm(), fp,
//					others);
//			if(answer.equalsIgnoreCase(RESOURCES.getString("prompt.no"))) {
//				return false;
//			}
//			else if(answer.equalsIgnoreCase(RESOURCES.getString("prompt.yes")) || answer.equals(fp)) {
//				return true;
//			}
//			answer = terminal.prompt(RESOURCES.getString("mismatchedHost.repeat"));
//			
//		} while(true);
		
		return true;
	}

	private Optional<Boolean> unknownHost(String host, SshPublicKey key) throws SshException, UnknownHostException {

//		var fp = key.getFingerprint();
//		var answer = terminal.prompt(RESOURCES.getString("unknownHost.content"), host, key.getJCEPublicKey().getAlgorithm(), fp);
//		
//		do {
//			if(answer.equalsIgnoreCase(RESOURCES.getString("prompt.always")) || answer.equals(fp)) {
//				return Optional.of(true);
//			}
//			else if(answer.equalsIgnoreCase(RESOURCES.getString("prompt.no"))) {
//				return Optional.empty();
//			}
//			else if(answer.equalsIgnoreCase(RESOURCES.getString("prompt.yes"))) {
//				return Optional.of(false);
//			}
//
//			answer = terminal.prompt(RESOURCES.getString("unknownHost.repeat"));
//			
//		} while(true);
		return Optional.of(false);
		
	}
}
