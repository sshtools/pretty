package com.sshtools.pretty.pricli.serial;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import com.sshtools.pretty.InetSocketAddressConverter;
import com.sshtools.pretty.serial.XYZModemProtocol;
import com.sshtools.terminal.xyzmodem.NioFileAdapter;
import com.sshtools.terminal.xyzmodem.ZModem;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "zmodem-send",
         aliases = {"zms", "zsend" },
         footer = "%nAliases: zms", 
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Send file using the ZMODEM protocol.")
public class ZModemSend extends AbstractModemSendOrReceive {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(ZModemSend.class.getName());
	
	@Parameters(index = "0", arity = "0..", paramLabel = "PATH", description = "Location of files to send")
	private List<Path> paths = new ArrayList<>();
	
	public final static class TCPGroup {
		@Option(names = {"-tc", "--tcp-client"}, converter = InetSocketAddressConverter.class, description = "Connect to TCP port at specified address instead of serial port")
		protected InetSocketAddress clientAddress;

		@Option(names = {"-ts", "--tcp-server"}, converter = InetSocketAddressConverter.class, description = "Listen on a TCP port at specified address instead of serial port")
		protected InetSocketAddress serverAddress;
	}
	
	@ArgGroup(exclusive = true, heading = "TCP options:%n", order = 1)
	private TCPGroup tcp;
	
	public ZModemSend() {
		super(RESOURCES);
	}

	@Override
	public Integer call() throws Exception {
		var paths = this.paths.isEmpty() ? openMultiplePaths() : this.paths;
		
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
				protocol.send(
					expandLocalList(paths.toArray(new Path[0]))
						.stream()
						.collect(Collectors.toMap(Path::toString, NioFileAdapter::new)),
					multiXYZTransferProgress());
			}

			@Override
			protected ZModem create(InputStream in, OutputStream out) {
				return new ZModem(in, out);
			}
		});
		
		parent.cli().result(MessageFormat.format(RESOURCES.getString("sent"), paths.size()));
		return 0;
	}
}
