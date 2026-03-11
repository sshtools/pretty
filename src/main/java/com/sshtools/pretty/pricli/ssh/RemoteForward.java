package com.sshtools.pretty.pricli.ssh;

import com.sshtools.client.SshClient;
import com.sshtools.common.forwarding.ForwardingRequest.ForwardingRequestBuilder;
import com.sshtools.common.permissions.UnauthorizedException;
import com.sshtools.common.ssh.SshException;

import picocli.CommandLine.Command;

@Command(name = "remote-forward", aliases = { "rfwd", "rfw" }, usageHelpAutoWidth = true, 
description = "Allow clients on the remote host or LAN to make connections to services running on your host or LAN.")
public class RemoteForward extends AbstractForward {
	
	public RemoteForward() {
	}
	
	@Override
	protected void startForwarding(SshClient ssh, ForwardingRequestBuilder bldr)
			throws UnauthorizedException, SshException {
		ssh.bindLocal(bldr.build());
		
	}

}
