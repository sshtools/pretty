package com.sshtools.pretty.serial;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.TooManyListenersException;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.pretty.Status;
import com.sshtools.pretty.Status.Unit;
import com.sshtools.pretty.Status.Width;
import com.sshtools.pretty.Strings;
import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TerminalProtocol;
import com.sshtools.pretty.pricli.serial.Serial.FlowControl;
import com.sshtools.pretty.pricli.serial.Serial.Parity;
import com.sshtools.pretty.pricli.serial.Serial.StopBits;
import com.sshtools.terminal.emulation.TerminalOutputStream;
import com.sshtools.terminal.emulation.TerminalViewport;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

import purejavacomm.SerialPort;
import purejavacomm.SerialPortEvent;

public class SerialProtocol implements TerminalProtocol, Status.Element {

	static Logger LOG = LoggerFactory.getLogger(SerialProtocol.class);

	private TTY tty;
	private Thread thread;
	private final SerialPort port;
	private final String name;
	private boolean disconnecting;
	private TerminalViewport<?, ?, ?> viewport;
	private final InputStream in;
	private final OutputStream out;

	public SerialProtocol(SerialPort port) {
		this.port = port;
		name = port.getName();
		try {
			in = port.getInputStream();
			out = port.getOutputStream();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		try {
			port.addEventListener(this::event);
		} catch (TooManyListenersException e) {
			throw new IllegalStateException("Failed to add event listener to serial port.", e);
		}
	}

	@Override
	public void attach(TTY tty) {
		if (this.tty != null)
			throw new IllegalStateException("Already connected to serial console.");

		this.tty = tty;

		thread = Thread.currentThread();
		disconnecting = false;

		var terminal = tty.terminal();
		viewport = terminal.getViewport();
		tty.status().add(this);

		viewport.setInput(this::send);
	}

	@Override
	public void send(byte[] data, int off, int len) {
		try {
			out.write(data, off, len);
		} catch (Exception e) {
			LOG.error("Failed to write to destination.", e);
		}
	}

	@Override
	public void decode() throws Exception {
		try {
			in.transferTo(new TerminalOutputStream(viewport));
		} catch (IOException e) {
			if (!disconnecting)
				throw new UncheckedIOException(e);
		} finally {
			LOG.info("Out of serial loop.");
			disconnecting = false;
		}
	}

	@Override
	public void detach() {
		if (this.tty == null)
			throw new IllegalStateException("Not connected to serial port.");

		try {
			LOG.info("Detaching serial protocol from port {}", port.getName());
			tty.status().remove(this);
			disconnecting = true;
			var terminal = tty.terminal();
			var viewport = terminal.getViewport();
			viewport.setInput(null);
		} finally {
			tty = null;
		}
	}

	@Override
	public boolean isAttached() {
		return tty != null;
	}

	@Override
	public void close() {
		port.removeEventListener();
		if (this.tty != null) {
			detach();
		}
		thread.interrupt();
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
		bldr.append(Strings.trimPad(String.format("%-10s %7d %s", displayName(), baudRate(), mnemonics()), cols));
		bldr.style(AttributedStyle.INVERSE_OFF);
		vp.write(bldr.toAnsi().getBytes(vp.getCharacterSet()));
	}
	
	private void event(SerialPortEvent event) {
//		if (event.getEventType() == SerialPortEvent.BI || event.getEventType() == SerialPortEvent.OE
//				|| event.getEventType() == SerialPortEvent.FE || event.getEventType() == SerialPortEvent.PE) {
//			LOG.warn("Serial port {} error: {}", port.getName(), event.getEventType());
//		}
		tty.terminal().getViewport().enqueue(() -> {
			tty.status().redraw(false);			
		});

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
		if(port.isCD()) {
			b.append(" CD");
		}
		if(port.isCTS()) {
			b.append(" CTS");
		}
		if(port.isDSR()) {
			b.append(" DSR");
		}
		if(port.isDTR()) {
			b.append(" DTR");
		}
		if(port.isRI()) {
			b.append(" RI");
		}
		if(port.isRTS()) {
			b.append(" RTS");
		}
		return b.toString();
	}

	@Override
	public void cwd(String cwd) {
	}
}
