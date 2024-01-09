package com.sshtools.pretty.pricli;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import picocli.CommandLine.Command;

@Command(name = "lpwd", 
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Display current local directory")
public class Lpwd extends LocalFileCommand {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Lpwd.class.getName());

	@Override
	public Integer call() throws Exception {
		Styling.styled(MessageFormat.format(RESOURCES.getString("lpwd"), parent.cli().cwd()))
				.println(parent.cli().jline());
		return 0;
	}
}
