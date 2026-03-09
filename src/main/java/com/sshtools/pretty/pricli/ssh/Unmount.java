package com.sshtools.pretty.pricli.ssh;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import com.sshtools.pretty.pricli.Styling;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "unmount", aliases = { "um", "umnt",
		"umount" }, usageHelpAutoWidth = true, description = "Unmount remote directory from local filesystem")
public class Unmount extends SftpCommand {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Unmount.class.getName());

	@Parameters(index = "0", arity = "1", description = "Name of local mount")
	private String name;

	public Unmount() {
		super(FilenameCompletionMode.LOCAL);
	}

	@Override
	public Integer call() throws Exception {

		if (!ActiveMount.mounts.containsKey(name)) {
			throw new IllegalStateException(MessageFormat.format(RESOURCES.getString("notMounted"), name));
		}

		var mount = ActiveMount.mounts.remove(name).unmount();
		parent.cli()
				.result(Styling.styled(MessageFormat.format(RESOURCES.getString("unmounted"), name, mount.mountPoint()))
						.toAttributedString());
		return 0;

	}

}
