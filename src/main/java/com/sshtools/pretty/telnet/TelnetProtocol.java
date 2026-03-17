package com.sshtools.pretty.telnet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.pretty.Status.Element;
import com.sshtools.pretty.Status.Unit;
import com.sshtools.pretty.Status.Width;
import com.sshtools.pretty.Strings;
import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TerminalProtocol;
import com.sshtools.terminal.emulation.TerminalOutputStream;
import com.sshtools.terminal.emulation.TerminalViewport;
import com.sshtools.terminal.emulation.events.ResizeListener;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

/**
 * A {@link TerminalProtocol} implementation for telnet connections, bridging
 * a {@link TelnetClient} to the Pretty terminal emulator. This follows the
 * same patterns as {@code SshProtocol} and {@code SerialProtocol}.
 */
public final class TelnetProtocol implements TerminalProtocol, ResizeListener, Element {

	static final ResourceBundle RESOURCES = ResourceBundle.getBundle(TelnetProtocol.class.getName());
	static final Logger LOG = LoggerFactory.getLogger(TelnetProtocol.class);

	private TTY tty;
	private Thread thread;
	private final TelnetClient client;
	private boolean disconnecting;

	public TelnetProtocol(TelnetClient client) {
		this.client = client;
	}

	@Override
	public void attach(TTY tty) {
		if (this.tty != null)
			throw new IllegalStateException("Already attached to a terminal.");

		this.tty = tty;
		thread = Thread.currentThread();
		disconnecting = false;

		var terminal = tty.terminal();
		var viewport = terminal.getViewport();

		// Direct window resizes to the telnet NAWS sub-negotiation
		viewport.addResizeListener(this);

		// Direct terminal input back to telnet
		viewport.setInput(this::send);

		tty.status().add(this);
	}

	@Override
	public void send(byte[] data, int off, int len) {
		try {
			client.send(data, off, len);
		} catch (Exception e) {
			LOG.error("Failed to write to telnet destination.", e);
		}
	}

	@Override
	public void decode() throws Exception {
		try {
			client.processAndDecode(new TerminalOutputStream(tty.terminal().getViewport()));
		} catch (IOException e) {
			if (!disconnecting)
				throw new UncheckedIOException(e);
		} finally {
			LOG.info("Out of telnet loop.");
			disconnecting = false;
		}
	}

	@Override
	public void detach() {
		if (this.tty == null)
			throw new IllegalStateException("Not attached to a terminal.");

		try {
			LOG.info("Detaching telnet protocol from {}:{}", client.hostname(), client.port());
			tty.status().remove(this);
			disconnecting = true;
			var terminal = tty.terminal();
			var viewport = terminal.getViewport();
			viewport.setInput(null);
			viewport.removeResizeListener(this);
		} finally {
			tty = null;
		}
	}

	@Override
	public void bufferResized(TerminalViewport<?, ?, ?> terminal, int columns, int rows, boolean remote) {
		if (!remote) {
			try {
				client.sendWindowSize(columns, rows);
			} catch (IOException e) {
				LOG.error("Failed to send window size change to telnet server.", e);
			}
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
		if (thread != null) {
			thread.interrupt();
		}
		try {
			client.close();
		} catch (IOException e) {
			LOG.error("Failed to close telnet client.", e);
		}
	}

	@Override
	public String displayName() {
		return "telnet://" + client.hostname() + (client.port() != 23 ? ":" + client.port() : "");
	}

	@Override
	public Optional<String> canClose() {
		if (!client.isConnected()) {
			return Optional.empty();
		} else {
			return Optional.of(MessageFormat.format(RESOURCES.getString("close"), client.hostname(), client.port()));
		}
	}

	@Override
	public Width width() {
		return new Width(Unit.COLUMNS, 30);
	}

	@Override
	public void draw(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp, int cols) throws IOException {
		var bldr = new AttributedStringBuilder();
		bldr.style(AttributedStyle.INVERSE);
		bldr.append(Strings.trimPad(
				String.format("telnet %s:%d", client.hostname(), client.port()), cols));
		bldr.style(AttributedStyle.INVERSE_OFF);
		vp.write(bldr.toAnsi().getBytes(vp.getCharacterSet()));
	}

	/**
	 * @return the underlying telnet client
	 */
	public TelnetClient client() {
		return client;
	}

	@Override
	public void cwd(String cwd) {
		// Not applicable for telnet
	}
}
