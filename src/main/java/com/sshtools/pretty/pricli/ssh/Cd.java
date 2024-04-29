package com.sshtools.pretty.pricli.ssh;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "cd", usageHelpAutoWidth = true, description = "Change remote directory")
public class Cd extends SftpCommand {
	
	@Parameters(index = "0", arity="1", paramLabel="PATH", description = "change directory to PATH", defaultValue = ".")
	String path;
	
	public Cd() {
		super(FilenameCompletionMode.DIRECTORIES_REMOTE);
	}
	
	@Override
	public Integer call() throws Exception {
		var expandedPath = expandRemoteSingle(path);
		sftpClient().cd(expandedPath);
		parent.cli().result(expandedPath.toString());
		return 0;
	}
}
