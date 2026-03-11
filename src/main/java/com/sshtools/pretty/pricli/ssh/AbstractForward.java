package com.sshtools.pretty.pricli.ssh;

import java.net.InetAddress;
import java.util.Optional;
import java.util.ResourceBundle;

import com.sshtools.client.SshClient;
import com.sshtools.common.forwarding.ForwardingRequest;
import com.sshtools.common.forwarding.ForwardingRequest.ForwardingRequestBuilder;
import com.sshtools.common.permissions.UnauthorizedException;
import com.sshtools.common.ssh.SshException;

import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

public abstract class AbstractForward extends SftpCommand {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(AbstractForward.class.getName());

	@Option(names = {"-p", "--protocol"}, description = "Protocol to forward. If not specified, will try to guess based on bind and target specifications.")
	private Optional<ForwardingRequest.Protocol> protocol = Optional.empty();
	
    @Parameters(index = "0", arity = "1", description = 
    		"The bind specification for the listening side of the tunnel, which is either [<address>:]<port> for TCP forwarding or <socketPath> for domain socket forwarding. If address is not specified, it defaults to localhost.")
    private String bindSpec;

    @Parameters(index = "1", arity = "0..1", description = 
    		"The target specification for the sending side of the tunnel, which is either [<address>:]<port> for TCP forwarding or <socketPath> for domain socket forwarding. If address is not specified, it defaults to bind spec.")	
    private Optional<String> targetSpec;

	public AbstractForward() {
		super(FilenameCompletionMode.NONE);
	}
	
	@Override
	public Integer call() throws Exception {
		var ssh = sshClient();
		
		var bldr = ForwardingRequestBuilder.create();
		
		/*
		 * Detect protocol if not specified. If target spec is missing, assume TCP. If
		 * both bind and target specs contain colons, assume TCP. If bind spec starts
		 * with a slash, assume domain sockets. Otherwise, try to resolve the bind spec
		 * as an address - if that succeeds, assume TCP, otherwise assume domain
		 * sockets.
		 */
		var protocol = this.protocol.orElseGet(() -> {
			if(targetSpec.isEmpty()) {
				return ForwardingRequest.Protocol.TCP;
			}
			else if(bindSpec.contains(":") && targetSpec.get().contains(":")) {
				return ForwardingRequest.Protocol.TCP;
			}
			else if(bindSpec.startsWith("/")) {
				return ForwardingRequest.Protocol.DOMAIN_SOCKETS;
			}
			else {
				try {
					InetAddress.getByName(bindSpec);
					return ForwardingRequest.Protocol.TCP;
				}
				catch(Exception e) {
					return ForwardingRequest.Protocol.DOMAIN_SOCKETS;
				}
			}
		});
		bldr.withProtocol(protocol);
		
		if(protocol == ForwardingRequest.Protocol.TCP) {
			targetSpec.ifPresentOrElse(ts -> {
				bldr.withBind(bindSpec);
				bldr.withDestination(ts);
			}, () -> {
				bldr.withBind(bindSpec);
				bldr.withDestination(bindSpec);
			});
		}
		else {
			targetSpec.ifPresentOrElse(ts -> {
				bldr.withPath(bindSpec);
				bldr.withDestinationPath(ts);
			}, () -> {
				bldr.withPath(bindSpec);
				bldr.withDestinationPath(bindSpec);
			});
		}
		
		
		startForwarding(ssh, bldr);
		
		parent.cli().result(RESOURCES.getString("forwarding"));
		
		return 0;
	}

	protected abstract void startForwarding(SshClient ssh, ForwardingRequestBuilder bldr)
			throws UnauthorizedException, SshException;

}
