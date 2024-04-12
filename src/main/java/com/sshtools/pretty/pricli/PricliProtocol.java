package com.sshtools.pretty.pricli;

import java.util.Optional;
import java.util.ResourceBundle;

import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;

import com.sshtools.pretty.Shells;
import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TerminalProtocol;
import com.sshtools.terminal.emulation.TerminalViewport;
import com.sshtools.terminal.emulation.events.ResizeListener;

public class PricliProtocol implements TerminalProtocol, ResizeListener {

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(PricliProtocol.class.getName());
	
	private TTY tty;
	private Thread thread;
	private Terminal jline;
	private String cmd;
	
	public PricliProtocol(String cmd) {
		this.cmd = cmd;
	}
	
	@Override
	public void attach(TTY tty) {
		if(this.tty != null)
			throw new IllegalStateException("Already connected to native console.");
		
		this.tty = tty;
		this.thread = Thread.currentThread();
		
		var terminal = tty.terminal();
		var vp = terminal.getViewport();
		
		jline = TTY.ttyJLine(vp.getTerminalType().getId(), vp);
		
		var cmd =this.cmd == null || this.cmd.equals(Shells.PRICLI) || this.cmd.equals("") ? null : this.cmd;
		while(true) {
			try(var shell = new PricliShell(terminal, jline, tty, tty.ttyContext()) {
				@Override
				public void close() {
					// TODO Auto-generated method stub
				}
			}) {
				var reader = LineReaderBuilder.builder().terminal(jline).build();
				shell.attach(reader);
				tty.attached(this);
		
				if(cmd == null) {
					shell.readCommands();
				}
				else {
					try {
						shell.execute(cmd);
					}
					finally {
						this.cmd = null;
					}
				}
				
				var xstr = Styling.styled(RESOURCES.getString("returnToExit")).toAnsi(jline);
				reader.readLine(xstr); 
		
				vp.addResizeListener(this);
			}
		}
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
	public void bufferResized(TerminalViewport<?, ?, ?> terminal, int columns, int rows, boolean remote) {
		jline.setSize(new Size(columns, rows));
	}

	@Override
	public void cwd(String cwd) {
	}

}
