package com.sshtools.pretty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.client.SessionChannelNG;
import com.sshtools.pretty.Status.Element;
import com.sshtools.pretty.Status.Unit;
import com.sshtools.pretty.Status.Width;
import com.sshtools.terminal.emulation.TerminalViewport;
import com.sshtools.terminal.emulation.TerminalOutputStream;
import com.sshtools.terminal.emulation.events.ResizeListener;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

public class SshProtocol implements TerminalProtocol, ResizeListener, Element {

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(SshProtocol.class.getName());

	static Logger LOG = LoggerFactory.getLogger(SshProtocol.class);
	
	private TTY tty;
	private Thread thread;
	private SessionChannelNG session;
	
	public SshProtocol(SessionChannelNG session) {
		this.session = session;
	}

	@Override
	public void attach(TTY tty) {
		if(this.tty != null)
			throw new IllegalStateException("Already connected to native console.");
		
		this.tty = tty;
		
		thread = Thread.currentThread();
		
		var terminal = tty.terminal();
		var viewport = terminal.getViewport();
		
		
		// Direct window resizes to pty
		viewport.addResizeListener(this);
		
		// Direct terminal input back to pty
		viewport.setInput((data, off, len) -> { 
			try {
				session.sendData(data, off, len);
			}
			catch(Exception e) {
				LOG.error("Failed to write to destination.", e);
			}
		});
		
		tty.status().add(this);

		try {
			session.getInputStream().transferTo(new TerminalOutputStream(viewport));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			LOG.info("Out of SSH loop.");
		}
	}
	
	@Override
	public void detach() {
		if(this.tty == null)
			throw new IllegalStateException("Not connected to native console.");

		try {
			tty.status().remove(this);
			var terminal = tty.terminal();
			var viewport = terminal.getViewport();
			viewport.setInput(null);
			viewport.removeResizeListener(this);
			thread.interrupt();
		}
		finally {
			tty = null;
		}
	}

	@Override
	public void bufferResized(TerminalViewport terminal, int columns, int rows, boolean remote) {
		if (!remote)
			session.changeTerminalDimensions(columns, rows, 0, 0);
		
	}

	@Override
	public void close() {
		if(this.tty != null) {
			detach();
		}
		try {
			session.close();
		}
		finally {
			session.getConnection().disconnect();
		}
	}

	@Override
	public String displayName() {
		return "ssh://" + session.getConnection().getUsername() + "@" + session.getConnection().getRemoteIPAddress();
	}

	@Override
	public Optional<String> canClose() {
		if(session == null || session.isClosed()) {
			return Optional.empty();
		}
		else {
			return Optional.of(MessageFormat.format(RESOURCES.getString("close"), session.getConnection().getUsername(), session.getConnection().getRemoteIPAddress()));
		}
	}

	@Override
	public boolean isAttached() {
		return tty != null;
	}

	@Override
	public Width width() {
		return new Width(Unit.COLUMNS, 30);
	}

	@Override
	public void draw(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp, int cols) throws IOException {
		var bldr = new AttributedStringBuilder();
		bldr.style(AttributedStyle.INVERSE);
		bldr.append(Strings.trimPad(String.format("%s@%s", session.getConnection().getUsername(), session.getConnection().getRemoteIPAddress()), cols /= 2));
		bldr.append(' ');
		cols--;
		bldr.append(Strings.trimPad(session.getConnection().getRemoteIdentification(), cols));
		bldr.style(AttributedStyle.INVERSE_OFF);
		vp.write(bldr.toAnsi().getBytes(vp.getCharacterSet()));
	}
}
