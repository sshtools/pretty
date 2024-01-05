package com.sshtools.pretty;

import java.util.Optional;
import java.util.ResourceBundle;

import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;

import com.sshtools.pretty.pricli.Styling;
import com.sshtools.terminal.emulation.TerminalViewport;
import com.sshtools.terminal.emulation.events.ResizeListener;

public class ErrorProtocol implements TerminalProtocol, ResizeListener {

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(ErrorProtocol.class.getName());
	
	private TTY tty;
	private final Exception exception;
	private final String message;
	private Thread thread;
	private Terminal jline;
	
	public ErrorProtocol(String message, Exception exception) {
		this.message=  message;
		this.exception = exception;
	}

	@Override
	public void attach(TTY tty) {
		if(this.tty != null)
			throw new IllegalStateException("Already connected to native console.");
		
		this.tty = tty;
		this.thread = Thread.currentThread();
		
		var vp = tty.terminal().getViewport();
		
		jline = TTY.ttyJLine(vp.getTerminalType().getId(), vp);
		
		var reader = LineReaderBuilder.builder().terminal(jline).build();
		var wtr = jline.writer();
		wtr.write(Strings.ansiExceptionString(true, exception, message).toAnsi(jline));
		wtr.println();
		wtr.println();
		
		tty.attached(this);
		
		var xstr = Styling.styled(RESOURCES.getString("returnToExit")).toAnsi(jline);
		reader.readLine(xstr); 

		vp.addResizeListener(this);
	}

	@Override
	public void detach() {
		try {
			var terminal = tty.terminal();
			var viewport = terminal.getViewport();
			viewport.removeResizeListener(this);
			viewport.setInput(null);
			thread.interrupt();
		}
		finally {
			tty.detached(this);
			tty = null;
		}
	}

	@Override
	public void close() {
		if(this.tty != null) {
			detach();
		}
	}

	@Override
	public String displayName() {
		return "error";
	}

	@Override
	public Optional<String> canClose() {
		return Optional.empty();
	}

	@Override
	public boolean isAttached() {
		return tty != null;
	}

	@Override
	public void bufferResized(TerminalViewport<?, ?, ?> terminal, int columns, int rows, boolean remote) {
		jline.setSize(new Size(columns, rows));
	}

}
