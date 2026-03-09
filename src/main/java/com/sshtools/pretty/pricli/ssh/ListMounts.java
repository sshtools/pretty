package com.sshtools.pretty.pricli.ssh;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import com.sshtools.pretty.pricli.Styling;

import picocli.CommandLine.Command;

@Command(name = "list-mounts", aliases = { "lm", "lmount",
		"lmounts" }, usageHelpAutoWidth = true, description = "List active mounts")
public class ListMounts extends SftpCommand {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(ListMounts.class.getName());

	@Override
	public Integer call() throws Exception {
		
		ActiveMount.mounts.entrySet().stream()
				.map(e -> MessageFormat.format(RESOURCES.getString("listEntry"), e.getValue().fs().root(), e.getKey(), e.getValue().mountPoint()))
				.forEachOrdered(e -> Styling.styled(e).println(parent.cli().jline()));
		return 0;
	}

}
