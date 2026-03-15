package com.sshtools.pretty.pricli.serial;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;

import com.sshtools.pretty.Constants;
import com.sshtools.pretty.Strings;
import com.sshtools.pretty.serial.XYZModemProtocol;
import com.sshtools.terminal.xyzmodem.YModem;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "ymodem-receive",
         aliases = {"ymr", "yrecv" },
         footer = "%nAliases: ymr, yrecv", 
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Receive file using the YMODEM protocol.")
public class YModemReceive extends AbstractModemSendOrReceive {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(YModemReceive.class.getName());
	
	@Parameters(index = "0", arity = "0..1", paramLabel = "PATH", description = "Location to save received file to. If batch mode, must be a directory, otherwise must be a single file.")
	private Optional<Path> path;
	
	@Option(names = {"-b", "--batch-mode"}, description = "Use batch mode. Implied if multiple files are specified, or if no path is specified and file browser is not used.")
	private boolean batchMode;
	
	@Option(names = {"-f", "--file-browser"}, description = "Use file browser to select directory or single path. When not supplied, and when no path is specified, will default to the current default Downloads directory from configuration.")
	private boolean fileBrowser;
	
	public YModemReceive() {
		super(RESOURCES);
	}

	@Override
	public Integer call() throws Exception {
		var batchMode = this.batchMode || (fileBrowser && !this.path.isPresent()) || this.path.filter(p -> Files.isDirectory(p)).isPresent();
		var path = this.path.orElseGet(() -> {
			if(fileBrowser) {
				if(batchMode) {
					return saveDirectory();
				}
				else {
					return saveSinglePath();
				}
			}
			else {
				return defaultDownloadsDirectory();
			}
		});
		
		
		if(batchMode && !Files.isDirectory(path)) {
			throw new IllegalStateException(RESOURCES.getString("batchMustBeDirectory"));
		}
		else if(!batchMode && Files.isDirectory(path)) {
			throw new IllegalStateException(RESOURCES.getString("singleMustBeFile"));
		}
			
		prompt();

		parent.tty().protocol(new XYZModemProtocol<YModem>() {
			{
				setDebug(debug);
			}

			@Override
			public void decode() throws Exception {
				if(batchMode) {
					protocol.receiveFilesInDirectory(path);
				}
				else {
					protocol.receive(path);
				}
			}

			@Override
			protected YModem create(InputStream in, OutputStream out) {
				return new YModem(in, out);
			}
		});
		
		parent.cli().result(MessageFormat.format(RESOURCES.getString("received"), path.getFileName().toString()));
		return 0;
	}
}
