package com.sshtools.pretty.pricli.ssh;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.sshtools.client.SshClient;
import com.sshtools.common.forwarding.ForwardingHandle;
import com.sshtools.common.forwarding.ForwardingRequest;
import com.sshtools.common.forwarding.ForwardingRequest.ForwardingType;
import com.sshtools.common.forwarding.ForwardingRequest.Protocol;
import com.sshtools.synergy.ssh.ForwardingManager;

import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

public abstract class AbstractSelectForward extends SftpCommand {

	@Option(names = { "-p",
			"--protocol" }, description = "Protocol to select. If not specified, will try to guess based on bind and target specifications.")
	protected Optional<ForwardingRequest.Protocol> protocol = Optional.empty();

	@Option(names = { "-t", "--type" }, description = "Type of forward. If not specified, all types will be selected.")
	protected Optional<ForwardingRequest.ForwardingType> type = Optional.empty();

	@Parameters(index = "0", arity = "0..1", description = "Identifiers of forward to select. May be a single <port> number or <host:port> pair, <path> for domain socket forwards, or the numeric ID of the forward as shown in the 'forwards' command. If not specified, all forwards matching the other criteria will be selected.")
	protected List<String> identifiers = new ArrayList<>();

	public AbstractSelectForward() {
		super(FilenameCompletionMode.NONE);
	}

	@Override
	public final Integer call() throws Exception {
		var ssh = sshClient();

		List<ForwardingHandle> fwds = new ArrayList<>();
		if (type.isEmpty()) {
			for (var t : ForwardingType.values()) {
				addAllForType(ssh, fwds, t);
			}
		} else {
			addAllForType(ssh, fwds, type.get());
		}

		if (fwds.isEmpty()) {
			noMatch();
		} else {
			if (fwds.size() == 1) {
				singleMatch(fwds.get(0));
			} else {
				matches(fwds);
			}
		}

		return 0;
	}

	protected abstract void matches(List<ForwardingHandle> fwds);

	protected void singleMatch(ForwardingHandle forwardingHandle) {
		matches(List.of(forwardingHandle));
	}

	protected abstract void noMatch();

	private void addAllForType(SshClient ssh, List<ForwardingHandle> fwds, ForwardingType t) {
		fwds.addAll(ForwardingManager.attached(t, ssh.getConnection().getConnectionProtocol()).stream()
				.filter(f -> protocol.isEmpty() || f.request().protocol() == protocol.get())
				.filter(this::matchesIdentifier)
				.toList());
	}

	protected boolean isMatchingTCP() {
		return protocol.isEmpty() || protocol.get() == ForwardingRequest.Protocol.TCP;
	}

	protected boolean isMatchingDomainSockets() {
		return protocol.isEmpty() || protocol.get() == ForwardingRequest.Protocol.DOMAIN_SOCKETS;
	}

	protected boolean matchesIdentifier(ForwardingHandle fwd) {
		if (identifiers.isEmpty()) {
			return true;
		} else {
			for (var id : identifiers) {

				if (ForwardingRequest.Protocol.DOMAIN_SOCKETS.equals(protocol.orElse(null))) {
					/* Only matching domain sockets, this must be a path */
					return fwd.boundPath().get().startsWith(id);
				} else if (ForwardingRequest.Protocol.TCP.equals(protocol.orElse(null))) {
					/* Only matching TCP, this must be a [<host>:]<port> id */
					return matchesTCPIdentifier(fwd, id);
				} else {
					/* Could be either, detect based on format of identifier */
					var idx = id.lastIndexOf(':');
					if (idx == -1) {
						/* Could be either path, just a port or just a host */
						try {
							Integer.parseInt(id.substring(idx + 1));

							/* Is host:port so this is TCP */
							return fwd.request().protocol() == Protocol.TCP && matchesTCPIdentifier(fwd, id);
						} catch (NumberFormatException e) {
							if (id.startsWith("\\") || id.startsWith("/")) {
								return fwd.request().protocol() == Protocol.DOMAIN_SOCKETS
										&& fwd.boundPath().get().startsWith(id);
							} else {
								try {
									Integer.parseInt(id.substring(idx + 1));
									return matchesTCPIdentifier(fwd, id);
								} catch (NumberFormatException e2) {
									return fwd.request().protocol() == Protocol.TCP && matchesTCPIdentifier(fwd, id);
								}
							}
						}
					} else {
						/* Must be a host:port, so this is TCP */
						return fwd.request().protocol() == Protocol.TCP && matchesTCPIdentifier(fwd, id);
					}
				}
			}

			return false;
		}
	}

	private boolean matchesTCPIdentifier(ForwardingHandle fwd, String id) {

		var idx = id.lastIndexOf(':');
		if (idx == -1) {
			/* Just a port or hostname */
			try {
				int port = Integer.parseInt(id);
				return fwd.boundPort().orElse(-1) == port;
			} catch (NumberFormatException e) {
				return fwd.request().bindAddress().equals(id);
			}
		} else {
			/* Host and port */
			return id.equals(fwd.request().bindSpec());
		}
	}

}
