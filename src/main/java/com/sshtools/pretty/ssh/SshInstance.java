package com.sshtools.pretty.ssh;

import java.io.IOException;

import com.sshtools.client.SshClient;
import com.sshtools.client.sftp.SftpClient;
import com.sshtools.common.permissions.PermissionDeniedException;
import com.sshtools.common.ssh.SshException;

public class SshInstance {

	private final SshClient client;
	private final SshConnector connector;
	private SftpClient sftp;

	public SshInstance(SshConnector connector, SshClient client) {
		this.client = client;
		this.connector = connector;
	}
	
	public SshConnector connector() {
		return connector;
	}
	
	public SshClient client() {
		return client;
	}

	public SftpClient sftp() {
		if(sftp == null) {
			try {
				sftp = SftpClient.SftpClientBuilder.create().withClient(client).build();
			} catch (SshException | PermissionDeniedException | IOException e) {
				throw new IllegalStateException("Failed to open SFTP client.", e);
			}
		}
		return sftp;
	}
}
