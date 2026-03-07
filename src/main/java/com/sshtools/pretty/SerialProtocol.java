package com.sshtools.pretty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.pretty.Status.Unit;
import com.sshtools.pretty.Status.Width;
import com.sshtools.pretty.pricli.Serial.FlowControl;
import com.sshtools.pretty.pricli.Serial.Parity;
import com.sshtools.pretty.pricli.Serial.StopBits;
import com.sshtools.terminal.emulation.TerminalOutputStream;
import com.sshtools.terminal.emulation.TerminalViewport;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

import purejavacomm.SerialPort;

public class SerialProtocol implements TerminalProtocol, Status.Element {

	static Logger LOG = LoggerFactory.getLogger(SerialProtocol.class);
	
	private TTY tty;
	private Thread thread;
	private final SerialPort port;
	private final String name;

	private boolean disconnecting;
	
	public SerialProtocol(SerialPort port) {
		this.port = port;
		name = port.getName();
	}

	@Override
	public void attach(TTY tty) {
		if(this.tty != null)
			throw new IllegalStateException("Already connected to serial console.");
		
		this.tty = tty;
		
		thread = Thread.currentThread();
		disconnecting = false;
		
		var terminal = tty.terminal();
		var viewport = terminal.getViewport();

		try {
			var in = port.getInputStream();
			var out = port.getOutputStream();
			
			viewport.setInput((data, off, len) -> { 
				try {
					out.write(data, off, len);
				}
				catch(Exception e) {
					LOG.error("Failed to write to destination.", e);
				}
			});
			tty.status().add(this);
			in.transferTo(new TerminalOutputStream(viewport));
		} catch (IOException e) {
			if(!disconnecting)
				throw new UncheckedIOException(e);
		} finally {
			LOG.info("Out of serial loop.");
			disconnecting = false;
		}
	}
	
	@Override
	public void detach() {
		if(this.tty == null)
			throw new IllegalStateException("Not connected to serial port.");

		try {
			LOG.info("Detaching serial protocol from port {}", port.getName());
			tty.status().remove(this);
			disconnecting = true;
			var terminal = tty.terminal();
			var viewport = terminal.getViewport();
			viewport.setInput(null);
			thread.interrupt();
		}
		finally {
			tty = null;
		}
	}

	@Override
	public boolean isAttached() {
		return tty != null;
	}

	@Override
	public void close() {
		if(this.tty != null) {
			detach();
		}
		port.close();
	}

	@Override
	public String displayName() {
		return name;
	}

	@Override
	public Optional<String> canClose() {
		return Optional.empty();
	}

	public SerialPort port() {
		return port;
	}

	@Override
	public Width width() {
		return new Width(Unit.COLUMNS, 24);
	}

	@Override
	public void draw(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp, int cols) throws IOException {
		var bldr = new AttributedStringBuilder();
		bldr.style(AttributedStyle.INVERSE);
		bldr.append(Strings.trimPad( 
			String.format("%-10s %7d %s", displayName(), baudRate(), mnemonics())
		, cols));
		bldr.style(AttributedStyle.INVERSE_OFF);
		vp.write(bldr.toAnsi().getBytes(vp.getCharacterSet()));
	}

	private int baudRate() {
		return port.getBaudRate();
	}

	private String mnemonics() {
		var b = new StringBuffer();
		b.append(port.getDataBits());
		b.append(Parity.fromValue(port.getParity()).toMnemonic());
		b.append(StopBits.fromValue(port.getStopBits()).toMnemonic());
		var fc = FlowControl.fromValue(port.getFlowControlMode());
		b.append(fc[0].toMnemonic());
		b.append(fc[1].toMnemonic());
		return b.toString();
	}

	@Override
	public void cwd(String cwd) {		
	}
}
