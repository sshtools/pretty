package com.sshtools.pretty.pricli.ssh;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "rm", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Remove file")
public class Rm extends SftpCommand {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Rm.class.getName());

	@Parameters(index = "0", arity = "1..", description = "File(s) to remove")
	private String[] files;
	
	@Option(names = "-f", description = "force deletion of children")
	private boolean force;
	
	@Option(names = "-r", description = "recursively delete directory and all children.")
	private boolean recursive;
	
	public Rm() {
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
