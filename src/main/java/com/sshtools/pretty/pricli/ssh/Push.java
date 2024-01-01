package com.sshtools.pretty.pricli.ssh;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;

import com.sshtools.client.sftp.RemoteHash;
import com.sshtools.client.tasks.PushTask.PushTaskBuilder;
import com.sshtools.common.ssh.SshException;
import com.sshtools.pretty.ssh.SshProtocol;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "push", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Push file to remote")
public class Push extends SftpCommand {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Push.class.getName());

	@Parameters(arity = "1..", paramLabel = "FILE", description = "one or more files/folders to transfer")
	Path[] files;

	@Option(names = { "-c",
			"--chunks" }, paramLabel = "COUNT", description = "the number of concurrent parts (chunks) to transfer")
	int chunks = 3;

	@Option(names = { "-M", "--multiplex" }, description = "multiplex channels over the same connection for each chunk")
	boolean multiplex;

	@Option(names = { "-v", "--verify" }, description = "verify the integrity of the remote file")
	boolean verifyIntegrity = false;

	@Option(names = { "-d", "--digest" }, paramLabel = "md5|sha1|sha256|sha512", description = "The digest to use")
	RemoteHash digest = RemoteHash.md5;

	@Option(names = {
			"-G", "--ignore-integrity" }, description = "ignore integrity check if remote server does not support a suitable SFTP extension")
	boolean ignoreIntegrity = false;

	//@Option(names = { "-F", "--force-sftp" }, description = "force the use of SFTP for all transfers (default is to use SCP)")
	//boolean forceSFTP;

	@Option(names = { "-r",
			"--remote-dir" }, paramLabel = "PATH", description = "the directory on the remote host you want to transfer the files to")
	Optional<String> remoteFolder;

	@Option(names = { "-a", "--async-requests" }, description = "the number of async requests to send", defaultValue = "0")
	int outstandingRequests;
	
	@Option(names = { "-b", "--blocksize" }, description = "the block size to use", defaultValue = "0")
	int blocksize; 
	
	@Option(names = { "-T", "--timing" }, description = "time the transfer operation")
	boolean timing;
	
	@Option(names = { "-B", "--verbose" }, description = "verbose progress output")
	boolean verboseOutput;
	
	public Push() {
		super(FilenameCompletionMode.LOCAL);
	}
	
	@Override
	public Integer call() throws Exception {

		var localFiles = expandLocalArray(files);
		var sshProto = (SshProtocol)parent.cli().tty().protocol();
		var sshConnector = sshProto.getInstance().connector();
		
		try (var pb = progressBarBuilder(RESOURCES.getString("push")).build()) {
			sshClient().runTask(PushTaskBuilder.create().
				withClients((idx) -> {
					if (multiplex || idx == 0)
						return sshClient();
					else {
						try {
							return sshConnector.connect(false, idx < 2);
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						} catch (SshException e) {
							throw new IllegalStateException(e);
						}
					}
				}).
				withPrimarySftpClient(sftpClient()).
				withPaths(localFiles).
				withChunks(chunks).
				withDigest(digest).
				withBlocksize(blocksize).
				withAsyncRequests(outstandingRequests).
				withRemoteFolder(expandRemoteSingle(remoteFolder)).
				withIntegrityVerification(verifyIntegrity).
				withIgnoreIntegrity(ignoreIntegrity).
				withVerboseOutput(verboseOutput).
				withProgressMessages((fmt, args) -> pb.setExtraMessage(MessageFormat.format(fmt, args))).
				withProgress(fileTransferProgress(pb, RESOURCES.getString("uploading"))).build());
		}

		return 0;
	}

}
