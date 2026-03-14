package com.sshtools.pretty.pricli.serial;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import com.sshtools.pretty.serial.XYZModemProtocol;
import com.sshtools.terminal.xyzmodem.YModem;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "ymodem-send",
         aliases = {"yms", "ysend" },
         footer = "%nAliases: yms", 
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Send file using the YMODEM protocol.")
public class YModemSend extends AbstractModemSendOrReceive {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(YModemSend.class.getName());
	
	@Option(names = {"-b", "--batch-mode"}, description = "Use batch mode. Implied if multiple files are specified")
	private boolean batchMode;
	
	@Parameters(index = "0", arity = "0..", paramLabel = "PATH", description = "Location of files to send")
	private List<Path> paths = new ArrayList<>();
	
	public YModemSend() {
		super(RESOURCES);
	}

	@Override
	public Integer call() throws Exception {
		var paths = this.paths.isEmpty() ? openMultiplePaths() : this.paths;
		var batch = batchMode || paths.size() > 1;
		
		prompt();

		parent.tty().protocol(new XYZModemProtocol<YModem>() {
			{
				setDebug(debug);
			}

			@Override
			public void decode() throws Exception {
				var all = expandLocalList(paths.toArray(new Path[0]));
				if(batch) {
					try (var pb = progressBarBuilder(MessageFormat.format(RESOURCES.getString("multiple"), paths.size()), "KiB", 1024).build()) {
						protocol.batchSend(xyzTransferListener(pb), all.toArray(new Path[0]));
					}
				}
				else {
					var path = all.get(0);
					try (var pb = progressBarBuilder(path.getFileName().toString(), "KiB", 1024).build()) {
						protocol.send(path, xyzTransferListener(pb));
					}
				}
			}

			@Override
			protected YModem create(InputStream in, OutputStream out) {
				return new YModem(in, out);
			}
		});
		
		parent.cli().result(MessageFormat.format(RESOURCES.getString("sent"), paths.size()));
		return 0;
	}
}
