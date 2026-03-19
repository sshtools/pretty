package com.sshtools.pretty.pricli;

import java.nio.file.Path;
import java.util.Optional;
import java.util.ResourceBundle;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.pretty.Actions.On;
import com.sshtools.pretty.Shells;
import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TTYContext;
import com.sshtools.pretty.TerminalProtocol;
import com.sshtools.terminal.emulation.Emulator;
import com.sshtools.terminal.emulation.events.ResizeListener;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

public class PricliProtocol implements TerminalProtocol, ResizeListener {
	
	private static final Logger LOG = LoggerFactory.getLogger(PricliProtocol.class);

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(PricliProtocol.class.getName());
	
	private TTY tty;
	private Thread thread;
	private Terminal jline;
	private String cmd;
	private JavaFXTerminalPanel terminal;
	private final DefaultHistory history;
	private final Path historyFile;
	
	public PricliProtocol(TTYContext ttyContext, String cmd) {
		this.cmd = cmd;
		
		history = new DefaultHistory();
		historyFile = ttyContext.getContainer().getConfiguration().dir().resolve(cmd == null ? "pricli.history" : cmd.split("\\s=")[0] + ".history");
	}
	
	@Override
	public void attach(TTY tty) {
		if(this.tty != null)
			throw new IllegalStateException("Already connected to native console.");
		
		this.tty = tty;
		this.thread = Thread.currentThread();
		
		terminal = tty.terminal();
		var vp = terminal.getViewport();
		
		jline = TTY.ttyJLine(vp.getTerminalType().getId(), vp);
		
		vp.addResizeListener(this);
	}

	@Override
	public void decode() throws Exception {
		var cmd =this.cmd == null || this.cmd.equals(Shells.PRICLI) || this.cmd.equals("") ? null : this.cmd;
		while(true) {
			try(var shell = new PricliShell(terminal, jline, tty, tty.ttyContext()) {
				@Override
				public void close() {
					// TODO Auto-generated method stub
				}
			}) {
				var reader = LineReaderBuilder.builder().
						history(history).
						terminal(jline).
						variable(LineReader.HISTORY_FILE, historyFile).
						build();
				shell.attach(reader);
				tty.attached(this);
	
				if(cmd == null) {
					shell.readCommands();
					break;
				}
				else {
					try {
						var result = shell.execute(On.CLI, cmd);
						LOG.info("Command result: {}", result);
						if(result == null || ( result instanceof Integer iresult  && iresult != 0 ) ) {
							reader.readLine(Styling.styled(RESOURCES.getString("returnToExit")).toAnsi(jline));
							return;
						}
						else {
							break;
						}
					}
					finally {
						this.cmd = null;
					}
				}
			}
		}
		
	}

	@Override
	public void detach() {
		try {
			LOG.info("Detaching for command {}", cmd);
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
		return "Pricli";
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
