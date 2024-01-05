package com.sshtools.pretty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import com.pty4j.unix.UnixPtyProcess;
import com.sshtools.pretty.Status.Element;
import com.sshtools.pretty.Status.Unit;
import com.sshtools.pretty.Status.Width;
import com.sshtools.terminal.emulation.TerminalOutputStream;
import com.sshtools.terminal.emulation.TerminalViewport;
import com.sshtools.terminal.emulation.events.ResizeListener;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

public class ConsoleProtocol implements TerminalProtocol, ResizeListener, Element {
	
	static Logger LOG = LoggerFactory.getLogger(ConsoleProtocol.class);
	
	public final static class Builder {
		private Optional<String> command = Optional.empty();
		private Optional<String[]> args = Optional.empty();
		private Optional<Path> workingDirectory = Optional.empty();
		private boolean cygwin;
		private boolean console;
		private boolean unixOpenTtyToPreserveOutputAfterTermination;
		private boolean useWinConPty;
		public boolean windowsAnsiColorEnabled;
		private Optional<Map<String, String>> env = Optional.empty();

		public ConsoleProtocol build() {
			return new ConsoleProtocol(this);
		}

		public Builder withCommandLine(String... commandAndArgs) {
			return withCommandLine(Arrays.asList(commandAndArgs));
		}
		
		public Builder withCommandLine(List<String> commandAndArgs) {
			if(commandAndArgs.size() > 0)
				withCommand(commandAndArgs.get(0));
			if(commandAndArgs.size() > 1)
				withArguments(commandAndArgs.subList(1,  commandAndArgs.size()));
			return this;
		}
	
		public Builder withArguments(String... args) {
			this.args = Optional.of(args);
			return this;
		}
	
		public Builder withArguments(Collection<String> args) {
			return withArguments(args.toArray(new String[0]));
		}
		
		public Builder withCommand(String command) {
			this.command = Optional.of(command);
			return this;
		}

		public Builder withConsole() {
			return withConsole(true);
		}
		
		public Builder withConsole(boolean console) {
			this.console = console;
			return this;
		}
		
		public Builder withCygwin() {
			return withConsole(true);
		}
		
		public Builder withCygwin(boolean cygwin) {
			this.cygwin= cygwin;
			return this;
		}
		
		public Builder withEnv(Map<String, String> env) {
			this.env = Optional.of(env);
			return this;
		}

		public Builder withPath(Path workingDirectory) {
			this.workingDirectory = Optional.of(workingDirectory);
			return this;
		}
		
		public Builder withUnixOpenTtyToPreserveOutputAfterTermination() {
			return withUnixOpenTtyToPreserveOutputAfterTermination(true);
		}
		
		public Builder withUnixOpenTtyToPreserveOutputAfterTermination(boolean unixOpenTtyToPreserveOutputAfterTermination) {
			this.unixOpenTtyToPreserveOutputAfterTermination = unixOpenTtyToPreserveOutputAfterTermination;
			return this;
		}
		
		public Builder withUseWinConPty(boolean useWinConPty) {
			this.useWinConPty = useWinConPty;
			return this;
		}
		
		public Builder withWindowsAnsiColorEnabled() {
			return withWindowsAnsiColorEnabled(true);
		}
		
		public Builder withWindowsAnsiColorEnabled(boolean windowsAnsiColorEnabled) {
			this.windowsAnsiColorEnabled = windowsAnsiColorEnabled;
			return this;
		}
	}
	
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(ConsoleProtocol.class.getName());
	
	private static long getCount(PtyProcess pty) {
		var ph = ProcessHandle.of(pty.pid());
		if(ph.isPresent()) {
			return ph.get().children().count();
		}
		return 0;
	}
	
	private static boolean hasChild(PtyProcess pty) {
		return getCount(pty) > 0;
	}
	
	private TTY tty;
	private PtyProcess pty;
	private Thread thread;
	private final Optional<String> command;
	private final Optional<String[]> args;
	private final Optional<Map<String, String>> env;
	private final Optional<Path> workingDirectory;
	private final boolean console;
	private final boolean cygwin;
	private final boolean unixOpenTtyToPreserveOutputAfterTermination;	
	private final boolean useWinConPty;
	private final boolean windowsAnsiColorEnabled;
	
	private ConsoleProtocol(Builder builder) {
		this.command = builder.command;
		this.args = builder.args;
		this.workingDirectory = builder.workingDirectory;
		this.console = builder.console;
		this.cygwin = builder.cygwin;
		this.useWinConPty = builder.useWinConPty;
		this.unixOpenTtyToPreserveOutputAfterTermination = builder.unixOpenTtyToPreserveOutputAfterTermination;
		this.windowsAnsiColorEnabled = builder.windowsAnsiColorEnabled;
		this.env = builder.env.map(Collections::unmodifiableMap);
	}

	@Override
	public void attach(TTY tty) {
		if(this.tty != null)
			throw new IllegalStateException("Already connected to native console.");
		
		this.tty = tty;
		
		thread = Thread.currentThread();
		
		var terminal = tty.terminal();
		var viewport = terminal.getViewport();
		
		synchronized(viewport.getBufferLock()) {
			// Process
			if(pty == null) {
				var bldr = new PtyProcessBuilder();
				bldr.setInitialRows(viewport.getRows());
				bldr.setInitialColumns(viewport.getColumns());
		
				// Shell
				command.ifPresentOrElse(c -> {
					args.ifPresentOrElse(as -> {
						var l = new ArrayList<String>();
						l.add(c);
						l.addAll(Arrays.asList(as));
						bldr.setCommand(l.toArray(new String[0]));
					}, () -> {
						bldr.setCommand(new String[] {c});	
					});
					
				}, () -> {
					if (System.getProperty("os.name").toLowerCase().contains("windows")) {
						bldr.setCommand(new String[] { "cmd.exe"/* , "/c", "start" */ });
					} else {
						var bash = "/bin/bash";
						if(Files.exists(Paths.get(bash))) {
			
							bldr.setCommand(new String[] { bash, "-l" });
						}
						else
							bldr.setCommand(new String[] { "/bin/sh", "-l"  });
					}
				});
				
				// Dir
				workingDirectory.ifPresent(dir -> bldr.setDirectory(dir.toString()));
		
				// Environment
				var env = new HashMap<>(this.env.orElseGet(System::getenv));
				env.putAll(tty.environment());
				bldr.setEnvironment(env);
				
				// Other
				bldr.setCygwin(cygwin);
				bldr.setConsole(console);
				bldr.setUnixOpenTtyToPreserveOutputAfterTermination(unixOpenTtyToPreserveOutputAfterTermination);
				bldr.setUseWinConPty(useWinConPty);
				bldr.setWindowsAnsiColorEnabled(windowsAnsiColorEnabled);
		
				// Start Process
				try {
					pty = bldr.start();
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
			
			// Direct window resizes to pty
			viewport.addResizeListener(this);
			
			// Direct terminal input back to pty
			viewport.setInput((data, offset, len) -> pty.getOutputStream().write(data, offset, len));
		}
		
		tty.attached(this);
		tty.status().add(this);

		try {
			pty.getInputStream().transferTo(new TerminalOutputStream(viewport));
		} catch (Error e) {
			LOG.error("Protocol failed.", e);
			throw e;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (Exception e) {
			LOG.error("Protocol failed.", e);
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void bufferResized(TerminalViewport<?,?,?> terminal, int columns, int rows, boolean remote) {
		if (!remote)
			pty.setWinSize(new WinSize(columns, rows));
		
	}

	@Override
	public Optional<String> canClose() {
		if(pty == null || !pty.isAlive() || !hasChild(pty)) {
			return Optional.empty();
		}
		else {
			return Optional.of(MessageFormat.format(RESOURCES.getString("close"),  getCount(pty)));
		}
	}

	@Override
	public void close() {
		if(this.tty != null) {
			detach();
		}
		pty.destroy();
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
			tty.detached(this);
			tty = null;
		}
	}

	@Override
	public String displayName() {
		if(pty instanceof UnixPtyProcess) {
			return Paths.get(((UnixPtyProcess)pty).getPty().getSlaveName()).getFileName().toString();
		}
		return RESOURCES.getString("console");
	}
	
	@Override
	public void draw(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp, int cols) throws IOException {
		var bldr = new AttributedStringBuilder();
		bldr.style(AttributedStyle.INVERSE);
		if(pty instanceof UnixPtyProcess) {
			bldr.append(Strings.trimPad(((UnixPtyProcess)pty).getPty().getSlaveName(), cols));
		}
		else {
			bldr.append(RESOURCES.getString("console"));
		}
		bldr.style(AttributedStyle.INVERSE_OFF);
		vp.write(bldr.toAnsi().getBytes(vp.getCharacterSet()));
	}

	@Override
	public boolean isAttached() {
		return tty != null;
	}

	@Override
	public Width width() {
		return new Width(Unit.COLUMNS, 10);
	}
}
