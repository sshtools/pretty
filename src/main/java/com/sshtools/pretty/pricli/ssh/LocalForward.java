package com.sshtools.pretty.pricli.ssh;

import com.sshtools.client.SshClient;
import com.sshtools.common.forwarding.ForwardingRequest.ForwardingRequestBuilder;
import com.sshtools.common.permissions.UnauthorizedException;
import com.sshtools.common.ssh.SshException;

import picocli.CommandLine.Command;

@Command(name = "local-forward", aliases = { "lfwd", "lfw" }, usageHelpAutoWidth = true, 
description = "Forward connections from your host or LAN on certain ports or unix domain sockets to a listener running on the other end of the tunnel.")
public class LocalForward extends AbstractForward {
	
	public LocalForward() {
	}
	
	@Override
	protected void startForwarding(SshClient ssh, ForwardingRequestBuilder bldr)
			throws UnauthorizedException, SshException {
		ssh.bindLocal(bldr.build());
		
	}

}
