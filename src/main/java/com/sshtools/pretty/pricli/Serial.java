package com.sshtools.pretty.pricli;

import static javafx.application.Platform.runLater;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.pretty.SerialProtocol;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import purejavacomm.CommPortIdentifier;
import purejavacomm.SerialPort;
import purejavacomm.UnsupportedCommOperationException;

@Command(name = "serial", 
         aliases = { "se" },
         footer = "%nAliases: se",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Connect to serial devices.",
				subcommands = { 
						Serial.Ports.class, 
						Serial.Connect.class, 
						Serial.Disconnect.class, 
						Serial.Set.class 
				})
public class Serial implements Callable<Integer> {
	static Logger LOG = LoggerFactory.getLogger(Serial.class);
	
	private final static AtomicInteger counter = new AtomicInteger(1);
	
	public enum DataBits {
		FIVE, SIX, SEVEN, EIGHT;
		
		public int toValue() {
			switch(this) {
			case FIVE:
				return SerialPort.DATABITS_5;
			case SIX:
				return SerialPort.DATABITS_6;
			case SEVEN:
				return SerialPort.DATABITS_7;
			case EIGHT:
				return SerialPort.DATABITS_8;
			default:
				throw new IllegalStateException();
			}
		}
		
		public static DataBits fromValue(int value) {
			switch(value) {
			case SerialPort.DATABITS_5:
				return FIVE;
			case SerialPort.DATABITS_6:
				return SIX;
			case SerialPort.DATABITS_7:
				return SEVEN;
			case SerialPort.DATABITS_8:
				return EIGHT;
			default:
				return EIGHT;
			}
		}
	}
	
	public enum Parity {
		NONE, ODD, EVEN, MARK, SPACE;
		
		public int toValue() {
			switch(this) {
			case NONE:
				return SerialPort.PARITY_NONE;
			case ODD:
				return SerialPort.PARITY_ODD;
			case EVEN:
				return SerialPort.PARITY_EVEN;
			case MARK:
				return SerialPort.PARITY_MARK;
			case SPACE:
				return SerialPort.PARITY_SPACE;
			default:
				throw new IllegalStateException();
			}
		}
		
		public static Parity fromValue(int value) {
			switch(value) {
			case SerialPort.PARITY_NONE:
				return NONE;
			case SerialPort.PARITY_ODD:
				return ODD;
			case SerialPort.PARITY_EVEN:
				return EVEN;
			case SerialPort.PARITY_MARK:
				return MARK;
			case SerialPort.PARITY_SPACE:
				return SPACE;
			default:
				return NONE;
			}
		}

		public char toMnemonic() {
			return name().charAt(0);
		} 
	}
	
	public enum StopBits {
		ONE, TWO, ONE_POINT_FIVE;
		
		public int toValue() {
			switch(this) {
			case ONE:
				return SerialPort.STOPBITS_1;
			case TWO:
				return SerialPort.STOPBITS_2;
			case ONE_POINT_FIVE:
				return SerialPort.STOPBITS_1_5;
			default:
				return SerialPort.STOPBITS_1;
			}
		}
		
		public static StopBits fromValue(int value) {
			switch(value) {
			case SerialPort.STOPBITS_1:
				return ONE;
			case SerialPort.STOPBITS_2:
				return TWO;
			case SerialPort.STOPBITS_1_5:
				return ONE_POINT_FIVE;
			default:
				throw new IllegalStateException();
			}
		}

		public char toMnemonic() {
			return name().charAt(0);
		} 
	}
	
	public enum FlowControl {
		NONE, RTS_CTS, XON_XOFF;
		
		public int toValue() {
			switch(this) {
			case NONE:
				return 0;
			case RTS_CTS:
				return 1;
			case XON_XOFF:
				return 4;
			default:
				throw new IllegalStateException();
			}
		}

		public char toMnemonic() {
			if(this == NONE)
				return '-';
			else
				return name().charAt(0);
		}
		
		public static int toValue(FlowControl in, FlowControl out) {
			return in.toValue() | out.toValue() << 1;
		}
		
		public static FlowControl[] fromValue(int value) {
			return new FlowControl[] { FlowControl.fromValueBits(value), FlowControl.fromValueBits(value >> 1) };
		}
		
		private static FlowControl fromValueBits(int value) {
			switch(value) {
			case 0:
				return NONE;
			case SerialPort.FLOWCONTROL_RTSCTS_IN:
				return RTS_CTS;
			case SerialPort.FLOWCONTROL_XONXOFF_IN:
				return XON_XOFF;
			default:
				return NONE;
			}
		} 
	}
	
	private static final String DEFAULT_SERIAL_OWNER_NAME = "Pretty";
	
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Serial.class.getName());

	@Parameters(index = "0", arity = "0..1", paramLabel = "PORT", description = "The serial port. Use 'ports' sub-command to list ports.")
	private Optional<String> port;

	@ParentCommand
	private ConnectionCommands parent;

	private Optional<SerialProtocol> active = Optional.empty();
	
	@Override
	public Integer call() throws Exception {
		if (port.isPresent()) {
			var portStr = port.get();
			if (active.isPresent() && portStr.equals(active.get().displayName())) {
				printPort(active.get().port());
			} else {
				var portId = CommPortIdentifier.getPortIdentifier(portStr);
				var port = (SerialPort) portId.open(DEFAULT_SERIAL_OWNER_NAME, 1000);
				try {
					printPort(port);
				} finally {
					port.close();
				}
			}
		} else {
			active.ifPresentOrElse(
					a -> Styling.styled(MessageFormat.format(RESOURCES.getString("active"), a.displayName()))
							.println(parent.cli().jline()),
					() -> Styling.styled(RESOURCES.getString("inactive")).println(parent.cli().jline()));
		}
		return 0;
	}

	private void printPort(SerialPort port) {
		var flow = FlowControl.fromValue(port.getFlowControlMode());
		printValue("br", "baud-rate", String.valueOf(port.getBaudRate()));
		printValue("db", "data-bits", DataBits.fromValue(port.getDataBits()).name());
		printValue("p", "parity", Parity.fromValue(port.getParity()).name());
		printValue("sb", "stop-bits", StopBits.fromValue(port.getStopBits()).name());
		printValue("fi", "flow-in", flow[0].name());
		printValue("fo", "flow-out", flow[1].name());
		printValue("ib", "in-buffer-size", String.valueOf(port.getInputBufferSize()));
		printValue("ob", "out-buffer-size", String.valueOf(port.getOutputBufferSize()));
		printValue("rf", "recv-framing-byte", String.valueOf(port.getReceiveFramingByte()));
		printValue("rt", "recv-threshold", String.valueOf(port.getReceiveThreshold()));
		printValue("r", "recv-timeout", String.valueOf(port.getReceiveTimeout()));
	}

	private void printValue(String abrv, String name, String value) {
		var cli = parent.cli();
		var jline = cli.jline();
		var as = new AttributedStringBuilder();
		as.style(new AttributedStyle().faint());
		as.append(String.format("[%-2s] ", abrv));
		as.style(new AttributedStyle().faintOff());
		as.append(String.format("%-18s = ", name));
		as.style(new AttributedStyle().bold());
		as.append(value);
		as.style(new AttributedStyle().boldOff());
		as.println(jline);
	}

	@Command(name = "ports", aliases = {
			"ps" }, usageHelpAutoWidth = true, description = "List available and in use ports.")
	public final static class Ports implements Callable<Integer> {
		
		public final static class PortOptions {
			@Option(names = {"-a", "--available" }, required = true)
			private boolean available;
			@Option(names = {"-u", "--in-use" }, required = true)
			private boolean inUse;
		}
		
		@ArgGroup(exclusive = true)
		private PortOptions portOptions;

		@ParentCommand
		private Serial parent;

		@Override
		public Integer call() throws Exception {
			var jline = parent.parent.cli().jline();
			var e = CommPortIdentifier.getPortIdentifiers();
			while (e.hasMoreElements()) {
				var portId = (CommPortIdentifier) e.nextElement();
				if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
					var as = new AttributedStringBuilder();
					as.append(String.format("%-20s", portId.getName()));
					if (portId.isCurrentlyOwned() && (portOptions == null ||(portOptions.inUse))) {
						as.style(new AttributedStyle().faint());
						var mine = parent.active.isPresent() && parent.active.get().displayName().equals(portId.getName());
						if (mine) {
							as.style(new AttributedStyle().underline());
						}
						as.append(MessageFormat.format(RESOURCES.getString("owned"), portId.getCurrentOwner()));
						as.style(new AttributedStyle().faintOff());
						if (mine) {
							as.style(new AttributedStyle().underlineOff());
						}
						as.println(jline);
					} else if (!portId.isCurrentlyOwned() && (portOptions == null || (portOptions.available))) {
						as.style(AttributedStyle.BOLD);
						as.append(RESOURCES.getString("available"));
						as.style(AttributedStyle.BOLD_OFF);
						as.println(jline);
					}
				}
			}
			return null;
		}

	}

	@Command(name = "set", aliases = {
			"s" }, usageHelpAutoWidth = true, description = "Set serial port parameters.")
	public static class Set implements Callable<Integer> {

		@ParentCommand
		protected Serial parent;
		
		@Option(names = {"--baud-rate", "baud-rate", "br", "-br", "--br" }, paramLabel = "BITS_PER_SECOND", description = "Baud rate in bits per second.")
		private Optional<Integer> baudRate;
		
		@Option(names = {"--data-bits", "data-bits", "db", "-db", "--db" }, paramLabel = "DATA_BITS", description = "Data bits.")
		private Optional<DataBits> dataBits;
		
		@Option(names = {"--stop-bits", "stop-bits", "sb", "-sb", "--sb" }, paramLabel = "STOP_BITS", description = "Stop bits.")
		private Optional<StopBits> stopBits;
		
		@Option(names = {"--parity", "parity", "pt", "-pt", "--pt" }, paramLabel = "PARITY", description = "Parity.")
		private Optional<StopBits> parity;
		
		@Option(names = {"--flow-in", "flow-in", "fi", "-fi", "--fi" }, paramLabel = "FLOW", description = "Input flow control.")
		private Optional<FlowControl> flowIn;
		
		@Option(names = {"--flow-out", "flow-out", "fo", "-fo", "--fo" }, paramLabel = "FLOW", description = "Output flow control.")
		private Optional<FlowControl> flowOut;
		
		@Option(names = {"--in-buffer-size", "in-buffer-size", "is", "-is", "--is" }, paramLabel = "BYTES", description = "Input buffer size.")
		private Optional<Integer> inBufferSize;
		
		@Option(names = {"--out-buffer-size", "out-buffer-size", "os", "-os", "--os" }, paramLabel = "BYTES",  description = "Output buffer size.")
		private Optional<Integer> outBufferSize;
		
		@Option(names = {"--rts", "-r" }, paramLabel = "BOOLEAN",  description = "Set or unset RTS if supported.", negatable = true)
		private Optional<Boolean> rts;
		
		@Option(names = {"--dtr", "-d" }, paramLabel = "BOOLEAN",  description = "Set or unset DTR if supported.", negatable = true)
		private Optional<Boolean> dtr;
		
		@Parameters(index = "0", arity = "0..1", paramLabel = "PORT", description = "The serial port to configure. Omit to use currently active port. Use 'ports' sub-command to list ports.")
		private Optional<String> port;

		@Override
		public Integer call() throws Exception {
			SerialPort portToConfigure;
			if(port.isPresent()) {
				var portId = CommPortIdentifier.getPortIdentifier(port.get());
				if(portId.isCurrentlyOwned()) {
					throw new IllegalStateException(MessageFormat.format(RESOURCES.getString("inUse"), port, portId.getCurrentOwner()));
				}
				portToConfigure = (SerialPort)portId.open(DEFAULT_SERIAL_OWNER_NAME, 2000);
			}
			else {
				if(parent.active.isPresent()) {
					portToConfigure = parent.active.get().port();
				}
				else {
					throw new IllegalStateException(RESOURCES.getString("noneActive"));
				}
			}
			try {
				configurePort(portToConfigure);
				parent.parent.cli().result(Styling.styled(MessageFormat.format(RESOURCES.getString("configured"), portToConfigure.getName())).toAttributedString());
				return 0;
			}
			catch(Exception e) {
				e.printStackTrace();
				throw e;
			}
			finally {
				if(port.isPresent()) {
					portToConfigure.close();
				}
			}
		}
		
		protected void configurePort(SerialPort serialPort) throws UnsupportedCommOperationException {
			if(baudRate.isPresent() || dataBits.isPresent() || stopBits.isPresent() || parity.isPresent()) {
				serialPort.setSerialPortParams(
						baudRate.orElseGet(serialPort::getBaudRate), 
						dataBits.map(db -> db.toValue()).orElseGet(serialPort::getDataBits), 
						stopBits.map(sb -> sb.toValue()).orElseGet(serialPort::getStopBits),
						parity.map(sb -> sb.toValue()).orElseGet(serialPort::getParity) 
				);
			}

			var currentFlow = serialPort.getFlowControlMode();
			var flow = currentFlow;
			if(flowIn.isPresent()) {
				flow = ( flow & 0x03 ) | flowIn.get().toValue();
			}
			if(flowOut .isPresent()) {
				flow = ( flow & 0x0C ) | ( flowOut.get().toValue() << 1 );
			}
			if(flow != currentFlow) {
				serialPort.setFlowControlMode(flow);
			}
			if(inBufferSize.isPresent()) {
				serialPort.setInputBufferSize(inBufferSize.get());
			}
			if(outBufferSize.isPresent()) {
				serialPort.setOutputBufferSize(outBufferSize.get());
			}
			if(rts.isPresent()) {
				serialPort.setRTS(rts.get());
			}
			if(dtr.isPresent()) {
				serialPort.setRTS(dtr.get());
			}
		}

	}

	@Command(name = "connect", aliases = {
			"c" }, usageHelpAutoWidth = true, description = "Connect to serial port.")
	public final static class Connect extends Set {
		
		@ParentCommand
		private Serial parent;
		
		@Option(names = {"-t","--timeout"}, description = "Connect timeout in seconds.", paramLabel = "TIMEOUT")
		private Optional<Integer> timeout;
		
		@Option(names = {"-H", "--no-pop"}, paramLabel = "NUMBER", description="Do not automatically return to the terminal on successful connection.")
		private boolean noPop;

		@Parameters(index = "0", arity = "1", paramLabel = "PORT", description = "The serial port to connect. Use 'ports' sub-command to list ports.")
		private String port;

		@Override
		public Integer call() throws Exception {
			if(parent.active.isPresent()) {
				if(parent.active.get().displayName().equals(port)) {
					throw new IllegalStateException(MessageFormat.format(RESOURCES.getString("alreadyConnected"), port));
				}
				else {
					throw new IllegalStateException(MessageFormat.format(RESOURCES.getString("connectedToOther"), parent.active.get().displayName(), port));
				}
			}

			if(parent.parent.tty().protocols().size() > 1) {
				throw new IllegalStateException(MessageFormat.format(RESOURCES.getString("connectedToOther"), parent.parent.tty().protocol().displayName()));
			}
			
			try {
				var portId = CommPortIdentifier.getPortIdentifier(port);
				if(portId.isCurrentlyOwned()) {
					throw new IllegalStateException(MessageFormat.format(RESOURCES.getString("inUse"), port, portId.getCurrentOwner()));
				}
				var serialPort = (SerialPort)portId.open(DEFAULT_SERIAL_OWNER_NAME, timeout.map(t -> t.intValue() * 1000).orElse(60000));
				try {
					configurePort(serialPort);
					var proto = new SerialProtocol(serialPort);
					parent.active = Optional.of(proto);
					parent.parent.cli().result(Styling.styled(MessageFormat.format(RESOURCES.getString("connected"), port, proto.port().getBaudRate())).toAttributedString());
	
					if(!noPop) {
						runLater(parent.parent.cli()::close);
					}
	
					new Thread(() -> {
						parent.parent.tty().protocol(proto);
					}, "Serial-" + counter.getAndIncrement()).start();
				}
				catch(Exception e) {
					try {
						serialPort.close();
					}
					catch(Exception e2) {
					}
					parent.active = Optional.empty();
					throw e;
				}
				
				
				return 0;
			}
			catch(Exception e) {
				LOG.error("Failed.", e);
				throw e;
			}				
		}

	}

	@Command(name = "disconnect", aliases = {
			"d" }, usageHelpAutoWidth = true, description = "Disconnect from serial port.")
	public final static class Disconnect implements Callable<Integer> {
		
		@ParentCommand
		private Serial parent;
		
		@Override
		public Integer call() throws Exception {
			if(parent.active.isPresent()) {
				var port = parent.active.get();
				try {
					port.close();
				}
				finally {
					parent.active = Optional.empty();
				}
				parent.parent.cli().result(Styling.styled(MessageFormat.format(RESOURCES.getString("disconnected"), port.displayName())).toAttributedString());
			}
			else {
				throw new IllegalStateException(RESOURCES.getString("notConnected"));
			}
			return 0;
		}

	}
}
