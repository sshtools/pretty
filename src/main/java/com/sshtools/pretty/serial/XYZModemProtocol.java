package com.sshtools.pretty.serial;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.pretty.Status;
import com.sshtools.pretty.Status.Unit;
import com.sshtools.pretty.Status.Width;
import com.sshtools.pretty.Strings;
import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TerminalInputMonitor;
import com.sshtools.pretty.TerminalProtocol;
import com.sshtools.terminal.emulation.TerminalInput;
import com.sshtools.terminal.emulation.Emulator;
import com.sshtools.terminal.emulation.Emulator.ByteFilter;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

public abstract class XYZModemProtocol<PROTO> implements TerminalProtocol, Status.Element, ByteFilter {

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(XYZModemProtocol.class.getName());
	
	static Logger LOG = LoggerFactory.getLogger(XYZModemProtocol.class);

	private TTY tty;
	private Thread thread;
	private Emulator<?, ?, ?> viewport;
	private PipedOutputStream pipeOut;
	private TerminalProtocol parentProtocol;
	private TerminalInputMonitor monitor;
	private boolean debug;
	private InetSocketAddress tcpServer;
	private InetSocketAddress tcpClient;
	
	protected PROTO protocol;

	private Socket socket;
	private ServerSocket serverSocket;

	private TerminalInput wasInput;
	

	public XYZModemProtocol() {
	}


	public InetSocketAddress getTcpServer() {
		return tcpServer;
	}


	public void setTcpServer(InetSocketAddress tcpServer) {
		this.tcpServer = tcpServer;
	}


	public InetSocketAddress getTcpClient() {
		return tcpClient;
	}


	public void setTcpClient(InetSocketAddress tcpClient) {
		this.tcpClient = tcpClient;
	}


	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	@Override
	public void attach(TTY tty) {
		if (this.tty != null)
			throw new IllegalStateException("Already connected to serial console.");
		
		this.tty = tty;

		thread = Thread.currentThread();

		var terminal = tty.terminal();
		viewport = terminal.getViewport();
		
		
		/* We need the encapsulating protocols output stream to be the actual destination
		 * of the XMODEM output, so we can send the file data to the remote host. 
		 */
		parentProtocol = tty.parentProtocol(tty.protocol());

		
		if(tcpServer == null && tcpClient == null) {
			/* Terminal I/O mode. The XYZMODEM protocol will travel over whatever the
			 * encapsulating protocol is. Preferably serial, but SSH or Console should
			 * work too in most circumstances.
			 */
			
			/* Capture keyboard input to viewport and only test for the interrupt key */
			monitor = new TerminalInputMonitor.Builder(tty.cli().terminal().getViewport())
					.onTerminateInterrupt()
					.build();
			
			/*
			 * Serial mode. The XYZMODEM protocol will be talking to the piped stream, and we
			 * will be forwarding bytes from the piped stream to the encapsulating protocol.
			 */

			var pipeIn = new PipedInputStream();
			try {
				pipeOut = new PipedOutputStream(pipeIn);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}

			/* Send XMODEM output to the previous viewport input (which may be sent to the remote 
			 * host over SSH or serial or whatever 
			 */
			protocol = create(pipeIn, new BufferedOutputStream(new OutputStream() {
				@Override
				public void write(int b) throws IOException {
					write(new byte[] { (byte) b }, 0, 1);
				}
	
				@Override
				public void write(byte[] b, int off, int len) throws IOException {
					if(debug && LOG.isInfoEnabled()) {
						if (len <= 256) {
							var sb = new StringBuilder();
							for (int i = off; i < off + len; i++) {
								if (sb.length() > 0) sb.append(' ');
								sb.append(String.format("%02X", b[i] & 0xFF));
							}
							LOG.info("SEND -> {} bytes: {}", len, sb);
						} else {
							LOG.info("SEND -> {} bytes: first=0x{} last=0x{}", len,
								String.format("%02X", b[off] & 0xFF),
								String.format("%02X", b[off + len - 1] & 0xFF));
						}
					}
					parentProtocol.send(b, off, len);
				}
			}));
			
			/* The encapsulating protocol will still be decoding (waiting for bytes from
			 * the remote to send to the emulator), but not attached to any keyboard input. 
			 */
			viewport.addFilter(this);
		}
		else {
			/* TCP mode. The XMODEM protocol will be talking directly to the socket. But
			 * this might be server or client mode. If TCP server mode we need to wait
			 * for the socket connecting to use */

			
			/* Reattach the parents keyboard input, its safe in TCP mode */
			wasInput = viewport.setInput(parentProtocol::send);
			
			if(tcpServer != null) {
				try {
					serverSocket = new ServerSocket(tcpServer.getPort(), 1, tcpServer.getAddress());
					try {
						var wtr = tty.cli().jline().writer();
						wtr.println(MessageFormat.format(RESOURCES.getString("waitingForTcpClient"), serverSocket.getLocalSocketAddress()));
						wtr.flush();
						LOG.info("Waiting for TCP client to connect to {}", tcpServer);
						socket = serverSocket.accept();

						wtr.println(MessageFormat.format(RESOURCES.getString("tcpClientConnected"), socket.getRemoteSocketAddress()));
						wtr.flush();
					}
					catch (IOException e) {
						closeServerSocket();
						throw e;
					}
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
			else {
				try {
					socket = new Socket(tcpClient.getHostName(), tcpClient.getPort());
				}
				catch(IOException e) {
					throw new UncheckedIOException(e);
				}
			}

			try {
				protocol = create(socket.getInputStream(), new BufferedOutputStream(socket.getOutputStream()));
			}
			catch(IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		
		tty.status().add(this);
	}
	
	protected abstract PROTO create(InputStream pipeIn2, OutputStream outputStream);

	@Override
	public boolean test(byte b) throws IOException {
		if(tty == null || tcpServer != null || tcpClient != null) {
			return true;
		}
		else {
			if(debug && LOG.isInfoEnabled()) {
				LOG.info("RECV <- 0x{} {}", String.format("%02X", b & 0xFF), byteToName(b));
			}
			pipeOut.write(b);
			// TODO unsure
			pipeOut.flush(); 
			return false;
		}
	}

	@Override
	public void detach() {
		if (this.tty == null)
			throw new IllegalStateException("Not connected to serial port.");

		try {
			if(pipeOut != null) {
				try {
					pipeOut.close();
				} catch (IOException e) {
					LOG.warn("Error closing pipe output stream", e);
				} finally {
					pipeOut = null;
				}
			}
			LOG.info("Detaching XMODEM protocol");
			tty.status().remove(this);
			var terminal = tty.terminal();
			var viewport = terminal.getViewport();
			viewport.removeFilter(this);
			if(monitor == null) {
				viewport.setInput(wasInput);
			}
			else {
				monitor.close();
			}
		} finally {
			tty = null;
			if(socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
					LOG.warn("Error closing socket", e);
				} finally {
					socket = null;
				}
			}
			if(serverSocket != null) {
				closeServerSocket();
			}
		}
	}


	protected void closeServerSocket() {
		try {
			serverSocket.close();
		} catch (IOException e) {
			LOG.warn("Error closing server socket", e);
		} finally {
			serverSocket = null;
		}
	}

	@Override
	public boolean isAttached() {
		return tty != null;
	}

	@Override
	public void close() {
		if (this.tty != null) {
			detach();
		}
		thread.interrupt();
	}

	@Override
	public String displayName() {
		return "XYZMODEM";
	}

	@Override
	public Optional<String> canClose() {
		return Optional.empty();
	}

	@Override
	public Width width() {
		return new Width(Unit.COLUMNS, 6);
	}

	@Override
	public void draw(Emulator<JavaFXTerminalPanel, ?, ?> vp, int cols) throws IOException {
		var bldr = new AttributedStringBuilder();
		bldr.style(AttributedStyle.INVERSE);
		bldr.append(Strings.trimPad(String.format("%-6ss", displayName()), cols));
		bldr.style(AttributedStyle.INVERSE_OFF);
		vp.write(bldr.toAnsi().getBytes(vp.getCharacterSet()));
	}
	
	@Override
	public void cwd(String cwd) {
	}

	private static String byteToName(byte b) {
		switch (b) {
			case 0x01: return "(SOH)";
			case 0x02: return "(STX)";
			case 0x04: return "(EOT)";
			case 0x06: return "(ACK)";
			case 0x15: return "(NAK)";
			case 0x18: return "(CAN)";
			case 0x43: return "('C')";
			default:
				if (b >= 0x20 && b < 0x7F)
					return "('" + (char) b + "')";
				return "";
		}
	}
}
