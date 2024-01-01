package com.sshtools.pretty.pricli;

import java.nio.file.Path;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "lcd",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Change local directory")
public class Lcd extends LocalFileCommand {

	@Parameters(index = "0", arity="1", paramLabel="PATH", description = "change directory to PATH", defaultValue = ".")
	Path path;
	
	public Lcd() {
		super(FilenameCompletionMode.DIRECTORIES_LOCAL);
	}
	
	@Override
	public Integer call() throws Exception {
		var resolvedPath = expandLocalSingle(path);
		parent.cli().setWorkingDirectory(resolvedPath);
		parent.cli().result(resolvedPath.toString());
		return 0;
	}
}
