package com.sshtools.pretty.pricli.ssh;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "umask", usageHelpAutoWidth = true, description = "Remove file")
public class Umask extends SftpCommand {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Umask.class.getName());

	@Parameters(index = "0", arity = "1", description = "Set the umask")
	private String umask;
	
	@Override
	public Integer call() throws Exception {
		sftpClient().umask(umask);
		parent.cli().result(MessageFormat.format(RESOURCES.getString("set"), umask));
		return 0;
	}
	
	
}
