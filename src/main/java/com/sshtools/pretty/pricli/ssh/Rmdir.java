package com.sshtools.pretty.pricli.ssh;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "rmdir", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Remove directory")
public class Rmdir extends SftpCommand {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Rmdir.class.getName());

	@Parameters(index = "0", arity = "1", description = "Directory to remove")
	private String file;
	
	public Rmdir() {
		super(FilenameCompletionMode.DIRECTORIES_REMOTE);
	}
	
	@Override
	public Integer call() throws Exception {
		var expandedPath = expandRemoteSingle(file);
		sftpClient().rmdir(expandedPath);
		parent.cli().result(MessageFormat.format(RESOURCES.getString("removedDirectory"), expandedPath));
		return 0;
	}

}
