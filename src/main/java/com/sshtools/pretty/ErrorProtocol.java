package com.sshtools.pretty;

import java.util.Optional;
import java.util.ResourceBundle;

import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.pretty.pricli.Styling;
import com.sshtools.terminal.emulation.Emulator;
import com.sshtools.terminal.emulation.events.ResizeListener;

public class ErrorProtocol implements TerminalProtocol, ResizeListener {
	static Logger LOG = LoggerFactory.getLogger(ErrorProtocol.class);

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
		var wtr = jline.writer();
		wtr.write(Strings.ansiExceptionString(true, exception, message).toAnsi(jline));
		wtr.println();
		wtr.println();
		
		tty.attached(this);

		vp.addResizeListener(this);
	}

	@Override
	public void decode() throws Exception {
		var reader = LineReaderBuilder.builder().terminal(jline).build();
		var xstr = Styling.styled(RESOURCES.getString("returnToExit")).toAnsi(jline);
		reader.readLine(xstr); 
	}

	@Override
	public void detach() {
		try {
			LOG.info("Detaching error protocol");
			var terminal = tty.terminal();
			var viewport = terminal.getViewport();
			viewport.removeResizeListener(this);
			viewport.setInput(null);
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
		thread.interrupt();
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
	public void bufferResized(Emulator<?, ?, ?> terminal, int columns, int rows, boolean remote) {
		jline.setSize(new Size(columns, rows));
	}

	@Override
	public void cwd(String cwd) {
	}

}
