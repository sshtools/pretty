package com.sshtools.pretty.pricli.ssh;

import java.nio.file.Path;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import com.sshtools.pretty.pricli.Styling;

import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "put", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Upload local file.")
public class Put extends SftpCommand implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Put.class.getName());

	@Parameters(index = "0", arity = "1..", description = "File to upload")
	private Path[] files;

	@Parameters(index = "1", arity = "0..1", description = "The remote path")
	private Optional<String> destination;

	@Option(names = { "-T", "--timing" }, description = "time the transfer operation")
	boolean timing;

	@Option(names = { "-a", "--async-requests" }, description = "the number of async requests to send", defaultValue = "0")
	int outstandingRequests;
	
	@Option(names = { "-b", "--blocksize" }, description = "the block size to use", defaultValue = "0")
	int blocksize; 
	
	public Put() {
		super(FilenameCompletionMode.LOCAL_THEN_REMOTE);
	}
	
	@Override
	public Integer call() throws Exception {

		var sftp = sftpClient();
		var term = parent.cli().jline();
		
		if(blocksize > 0) {
			sftp.setBlockSize(blocksize);
		}
		if(outstandingRequests > 0) {
			sftp.setMaxAsyncRequests(outstandingRequests);
		}
		
		var target = destination.orElse(sftp.pwd());
			expandLocalAndDo((path) -> {
				try (var pb = progressBarBuilder(target).build()) {
					sftp.put(path.toString(), target, fileTransferProgress(pb, Styling.styled(RESOURCES.getString("uploading")).toAnsi(term)));
				}
			}, true, files);
		
		return 0;
	}
}