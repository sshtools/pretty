package com.sshtools.pretty.pricli.ssh;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "mkdir", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Create directory")
public class Mkdir extends SftpCommand {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Mkdir.class.getName());

	@Parameters(index = "0", arity = "1", description = "Directory to create")
	private String directory;
	
	public Mkdir() {
		super(FilenameCompletionMode.DIRECTORIES_REMOTE);
	}
	
	@Override
	public Integer call() throws Exception {
		var sftp = sftpClient();
		var dir = expandRemoteSingle(directory);
		sftp.mkdir(dir);
		parent.cli().result(MessageFormat.format(RESOURCES.getString("lmkdir"), dir));
		return 0;
	}

}
