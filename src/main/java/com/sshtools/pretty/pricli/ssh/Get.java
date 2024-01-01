package com.sshtools.pretty.pricli.ssh;

import java.nio.file.Path;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import com.sshtools.pretty.pricli.Styling;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "get", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Download remote file")
public class Get extends SftpCommand implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Get.class.getName());

	@Parameters(index = "0", arity = "1..", description = "File(s) to download")
	private String[] remotePaths;

	@Parameters(index = "1", arity = "0..1", description = "Local directory to download to")
	private Optional<Path> localPath;

	@Option(names = { "-T", "--timing" }, description = "time the transfer operation")
	boolean timing;

	@Option(names = { "-a", "--async-requests" }, description = "the number of async requests to send", defaultValue = "0")
	int outstandingRequests;
	
	@Option(names = { "-b", "--blocksize" }, description = "the block size to use", defaultValue = "0")
	int blocksize; 
	
	public Get() {
		super(FilenameCompletionMode.REMOTE_THEN_LOCAL);
	}
	
	@Override
	public Integer call() throws Exception {
		var sftp = sftpClient();
		if(blocksize > 0) {
			sftp.setBlockSize(blocksize);
		}
		if(outstandingRequests > 0) {
			sftp.setMaxAsyncRequests(outstandingRequests);
		}
		var expandedLocalPath = expandLocalSingleOr(localPath);
		var term = parent.cli().jline();
		
		expandRemoteAndDo(remotePath -> {
			try (var pb = progressBarBuilder(remotePath).build()) {
				if (expandedLocalPath.isPresent())
					sftp.get(remotePath, expandedLocalPath.get().toString(),
							fileTransferProgress(pb, Styling.styled(RESOURCES.getString("downloading")).toAnsi(term)));
				else
					sftp.get(remotePath, fileTransferProgress(pb, Styling.styled(RESOURCES.getString("downloading")).toAnsi(term)));
			}

		}, true, remotePaths);

		return 0;
	}
}