package com.sshtools.pretty.pricli.serial;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;

import com.sshtools.pretty.InetSocketAddressConverter;
import com.sshtools.pretty.serial.XYZModemProtocol;
import com.sshtools.terminal.xyzmodem.NioFileAdapter;
import com.sshtools.terminal.xyzmodem.ZMOptions;
import com.sshtools.terminal.xyzmodem.ZModem;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "zmodem-receive",
         aliases = {"zmr", "zrecv" },
         footer = "%nAliases: zmr, zrecv", 
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Receive file using the ZMODEM protocol.")
public class ZModemReceive extends AbstractModemSendOrReceive {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(ZModemReceive.class.getName());
	
	@Parameters(index = "0", arity = "0..1", paramLabel = "PATH", description = "Location to save received file to. Must be a directory.")
	private Optional<Path> path;
	
	@Option(names = {"-O", "--options"}, description = "List of options. By default is ESC8 and ESCCTL")
	private ZMOptions[] zmOptions;
	
	public final static class TCPGroup {
		@Option(names = {"-tc", "--tcp-client"}, converter = InetSocketAddressConverter.class, description = "Connect to TCP port at specified address instead of serial port")
		protected InetSocketAddress clientAddress;

		@Option(names = {"-ts", "--tcp-server"}, converter = InetSocketAddressConverter.class, description = "Listen on a TCP port at specified address instead of serial port")
		protected InetSocketAddress serverAddress;
	}
	
	@ArgGroup(exclusive = true, heading = "TCP options:%n", order = 1)
	private TCPGroup tcp;
	
	
	public ZModemReceive() {
		super(RESOURCES);
	}

	@Override
	public Integer call() throws Exception {
		var path = this.path.orElseGet(this::saveDirectory);
		if(!Files.isDirectory(path)) {
			throw new IllegalStateException(RESOURCES.getString("mustBeDirectory"));
		}
			
		if(tcp == null || tcp.serverAddress == null) {
			prompt();			
		}

		parent.tty().protocol(new XYZModemProtocol<ZModem>() {
			{
				setDebug(debug);
				if(tcp != null && tcp.serverAddress != null) {
					setTcpServer(tcp.serverAddress);
				} else if(tcp != null && tcp.clientAddress != null) {
					setTcpClient(tcp.clientAddress);
				}
			}

			@Override
			public void decode() throws Exception {
				var p = expandLocalSingle(path);
				if(zmOptions == null) {
					protocol.receive(multiXYZTransferProgress(), new NioFileAdapter(p));
				}
				else {
					protocol.receive(new NioFileAdapter(p), multiXYZTransferProgress(), zmOptions);
				}
				
			}

			@Override
			protected ZModem create(InputStream in, OutputStream out) {
				return new ZModem(in, out);
			}
		});
		
		parent.cli().result(MessageFormat.format(RESOURCES.getString("received"), path.getFileName().toString()));
		return 0;
	}
}
