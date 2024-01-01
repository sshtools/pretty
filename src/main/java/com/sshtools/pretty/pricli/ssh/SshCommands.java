package com.sshtools.pretty.pricli.ssh;

import java.util.concurrent.Callable;

import com.sshtools.pretty.pricli.Pricli;
import com.sshtools.pretty.pricli.RootCommand;

import picocli.CommandLine.Command;

@Command(name = "ssh", description = "Commands that may be used when an SSH session is active", 
		subcommands = {
		Cd.class, Chgrp.class, Chmod.class, Df.class, Get.class, Ln.class, Ls.class, Mkdir.class, Pull.class, Push.class, Put.class, Pwd.class, Rename.class, Rm.class, Rmdir.class, Symlink.class, Umask.class})
public final class SshCommands implements Callable<Integer>, RootCommand {

	private final Ssh ssh;
	private final Pricli cli;

	SshCommands(Ssh ssh, Pricli cli) {
		this.ssh = ssh;
		this.cli = cli;
	}
	
	public Ssh ssh() {
		return ssh;
	}

	@Override
	public Integer call() throws Exception {
		return 0;
	}

	@Override
	public Pricli cli() {
		return cli;
	}

}