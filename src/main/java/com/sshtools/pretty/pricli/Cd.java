package com.sshtools.pretty.pricli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "cd", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Change local directory.")
public class Cd implements Callable<Integer> {
	
	@Parameters(index = "0", arity="1", paramLabel="PATH", description = "Path")
	private String path;
	
	@Override
	public Integer call() throws Exception {
		return 0;
	}
}
