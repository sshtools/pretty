package com.sshtools.pretty.pricli.serial;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;

import com.sshtools.pretty.serial.XYZModemProtocol;
import com.sshtools.terminal.xyzmodem.XModem;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "xmodem-send",
         aliases = {"xms", "xsend" },
         footer = "%nAliases: xms", 
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Send file using the XMODEM protocol.")
public class XModemSend extends AbstractModemSendOrReceive {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(XModemSend.class.getName());
	
	@Option(names = {"-1", "--1k-block"}, description = "Use 1K blocks (XMODEM-1K)")
	private boolean use1KBlocks;
	
	@Parameters(index = "0", arity = "0..1", paramLabel = "PATH", description = "Location of file to send")
	private Optional<Path> path;
	
	public XModemSend() {
		super(RESOURCES);
	}

	@Override
	public Integer call() throws Exception {
		var path = this.path.orElseGet(this::openSinglePaths);
		
		prompt();
		
		parent.tty().protocol(new XYZModemProtocol<XModem>() {
			{
				setDebug(debug);
			}

			@Override
			public void decode() throws Exception {
				var p = expandLocalSingle(path);
				try (var pb = progressBarBuilder(p.getFileName().toString(), "KiB", 1024).build()) {
					protocol.send(p, use1KBlocks, xyzTransferListener(pb));
				}
			}

			@Override
			protected XModem create(InputStream in, OutputStream out) {
				return new XModem(in, out);
			}
		});
		
		parent.cli().result(MessageFormat.format(RESOURCES.getString("sent"), path.getFileName().toString()));
		return 0;
	}
}
