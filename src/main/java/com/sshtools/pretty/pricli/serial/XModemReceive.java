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
import picocli.CommandLine.Parameters;

@Command(name = "xmodem-receive",
         aliases = {"xmr", "xrecv" },
         footer = "%nAliases: xmr, xrecv", 
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Receive file using the XMODEM protocol.")
public class XModemReceive extends AbstractModemSendOrReceive {

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(XModemReceive.class.getName());
	
	@Parameters(index = "0", arity = "0..1", paramLabel = "PATH", description = "Location to save received file to")
	private Optional<Path> path;
	
	public XModemReceive() {
		super(RESOURCES);
	}
	
	@Override
	public Integer call() throws Exception {
		var path = this.path.orElseGet(this::saveSinglePath);
		
		prompt();
		
		parent.tty().protocol(new XYZModemProtocol<XModem>() {

			@Override
			public void decode() throws Exception {
				var p = expandLocalSingle(path);
				try (var pb = progressBarBuilder(p.getFileName().toString(), "KiB", 1024).build()) {
					protocol.receive(p, xyzTransferListener(pb));
				}
			}

			@Override
			protected XModem create(InputStream in, OutputStream out) {
				return new XModem(in, out);
			}
		});
		
		parent.cli().result(MessageFormat.format(RESOURCES.getString("received"), path.getFileName().toString()));
		return 0;
	}
}
