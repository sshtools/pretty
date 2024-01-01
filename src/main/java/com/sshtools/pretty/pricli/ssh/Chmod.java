package com.sshtools.pretty.pricli.ssh;

import com.sshtools.common.sftp.PosixPermissions.PosixPermissionsBuilder;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "chmod", usageHelpAutoWidth = true, mixinStandardHelpOptions = false, description = "Change permissions of file path")
public class Chmod extends SftpCommand  {

	@Option(names = { "-h" }, description = "Do not follow symlinks")
	private boolean dontFollowSymlinks;

	@Parameters(index = "0", description = "The new permissions")
	private String perms;

	@Parameters(index = "1", arity = "1", description = "Path to change group of")
	private String path;
	
	public Chmod() {
		super(FilenameCompletionMode.REMOTE);
	}

	@Override
	public Integer call() throws Exception {
		expandRemoteAndDo((fp) -> sftpClient().chmod(PosixPermissionsBuilder.create().
					withChmodArgumentString(perms).build(), fp), false, path);
		return 0;
	}

}
