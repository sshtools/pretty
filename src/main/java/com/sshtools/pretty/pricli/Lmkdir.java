package com.sshtools.pretty.pricli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ResourceBundle;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "lmkdir",
         aliases = { "lmd" },
         footer = "%nAliases: lmd",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Create local directory")
public class Lmkdir extends LocalFileCommand {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Lmkdir.class.getName());

	@Parameters(index = "0", arity = "1", description = "Directory to create")
	private Path directory;

	public Lmkdir() {
		super(FilenameCompletionMode.DIRECTORIES_LOCAL);
	}

	@Override
	public Integer call() throws Exception {
		var dir = expandLocalSingle(directory);
		Files.createDirectories(dir);
		parent.cli().result(MessageFormat.format(RESOURCES.getString("lmkdir"), dir));
		return 0;
	}

}
