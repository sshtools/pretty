package com.sshtools.pretty.pricli.ssh;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "forward", usageHelpAutoWidth = true, description = "Forward ports or unix domain sockets either to the remote or to the local side. The syntax for port forwarding is [bind_address:]port:host:hostport and for unix domain socket forwarding is [bind_address:]port:remote_socket or [bind_address:]local_socket:port. If bind_address is not specified, it defaults to localhost. If host is not specified, it defaults to localhost. Port forwarding can be used to forward a local port to a remote service or to forward a remote port to a local service. Unix domain socket forwarding can be used to forward a local socket to a remote service or to forward a remote socket to a local service.")
public class Forward extends SftpCommand {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Forward.class.getName());

	@Parameters(index = "0", arity = "1..", description = "File(s) to remove")
	private String[] files;
	
	@Option(names = "-f", description = "force deletion of children")
	private boolean force;
	
	@Option(names = "-r", description = "recursively delete directory and all children.")
	private boolean recursive;
	
	public Forward() {
		super(FilenameCompletionMode.REMOTE);
	}
	
	@Override
	public Integer call() throws Exception {
		var sftp = sftpClient();
		expandRemoteAndDo(p -> {
			sftp.rm(p, force, recursive);
		}, true, files);
		if(files.length == 1)
			parent.cli().result(MessageFormat.format(RESOURCES.getString("removedFile"), files[0]));
		else
			parent.cli().result(MessageFormat.format(RESOURCES.getString("removedFiles"), files.length));
		return 0;
	}

}
