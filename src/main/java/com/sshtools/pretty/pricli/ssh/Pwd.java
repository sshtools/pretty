package com.sshtools.pretty.pricli.ssh;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import com.sshtools.pretty.pricli.Styling;

import picocli.CommandLine.Command;

@Command(name = "pwd", usageHelpAutoWidth = true, description = "Display current remote directory")
public class Pwd extends SftpCommand {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Pwd.class.getName());

	@Override
	public Integer call() throws Exception {
		Styling.styled(MessageFormat.format(RESOURCES.getString("pwd"), sftpClient().pwd()))
				.println(parent.cli().jline());
		return 0;
	}
}
