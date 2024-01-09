package com.sshtools.pretty.ssh;

import java.io.IOException;

import org.jline.utils.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.client.SshClient;
import com.sshtools.client.sftp.SftpClient;
import com.sshtools.common.permissions.PermissionDeniedException;
import com.sshtools.common.sftp.SftpStatusException;
import com.sshtools.common.ssh.SshException;
import com.sshtools.pretty.pricli.Serial;

public class SshInstance {
	static Logger LOG = LoggerFactory.getLogger(SshInstance.class);

	private final SshClient client;
	private final SshConnector connector;
	private SftpClient sftp;
	private String cwd;

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
				var bldr = SftpClient.SftpClientBuilder.create();
				if(cwd != null) {
					bldr.withRemotePath(cwd);
				}
				sftp = bldr.withClient(client).build();
			} catch (SshException | PermissionDeniedException | IOException e) {
				throw new IllegalStateException("Failed to open SFTP client.", e);
			}
		}
		return sftp;
	}
	
	public void cwd(String cwd) {
		this.cwd = cwd;
		if(sftp != null) {
			try {
				sftp.cd(cwd);
			} catch (SftpStatusException | SshException e) {
				LOG.error("Failed to change SFTP remote directory to {}", cwd);
			}
		}
	}
}
