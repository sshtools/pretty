package com.sshtools.pretty.pricli;

import static javafx.application.Platform.runLater;

import java.io.Closeable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jline.console.CommandRegistry;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.MaskingCallback;
import org.jline.reader.Parser;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Curses;
import org.jline.utils.InfoCmp.Capability;
import org.jline.widget.AutosuggestionWidgets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.pretty.Actions.On;
import com.sshtools.pretty.ConsoleProtocol;
import com.sshtools.pretty.Constants;
import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TTYContext;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

import picocli.CommandLine;
import picocli.shell.jline3.PicocliCommands.PicocliCommandsFactory;

public abstract class PricliShell implements Closeable {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(PricliShell.class.getName());
	
	static Logger LOG = LoggerFactory.getLogger(PricliShell.class);

	private final PicocliCommandsFactory factory;
	private final ExceptionHandler exceptionHandler;
	private final Terminal jline;
	private final PricliSystemRegistry systemRegistry;
	private final List<CommandRegistry> commandRegistries = new ArrayList<>();
	private final DefaultParser parser;
	private final static Path cwd = Paths.get(System.getProperty("user.dir"));
	
	private AtomicBoolean canExit = new AtomicBoolean();
	private Path workingDirectory = Paths.get(System.getProperty("user.dir"));
	private AttributedString result;
	private String rightPrompt;
	private JavaFXTerminalPanel terminal;
	private TTY tty;
	private LineReader reader;

	private boolean interactive;
	
	public PricliShell(JavaFXTerminalPanel terminal, Terminal jline, TTY tty, TTYContext ttyContext) {
		this.terminal = terminal;
		this.jline = jline;
		this.tty = tty;

		parser = new DefaultParser(); /* TODO windows / unix path parsing mode */
		factory = new PicocliCommandsFactory();		
		exceptionHandler = new ExceptionHandler(jline, () -> tty.ttyContext().getContainer().getConfiguration().debug().getBoolean(Constants.VERBOSE_EXCEPTIONS_KEY));
		
		var cmd = newCommand(new PricliCommands(ttyContext, this, tty));
		var picocliCommands = new PricliCommandRegistry(cmd);
		picocliCommands.name(RESOURCES.getString("app"));

		systemRegistry = new PricliSystemRegistry(parser, jline, PricliShell::getCwd, null);
		
//		systemRegistry.addCompleter(new Completers.FileNameCompleter());
		addCommandRegistry(picocliCommands);
		registry(RESOURCES.getString("ui"), new UICommands(ttyContext, this, tty));
		registry(RESOURCES.getString("localFileSystem"), new LocalFileSystemCommands(ttyContext, this, tty));
		registry(RESOURCES.getString("connections"), new ConnectionCommands(ttyContext, this, tty));
		registry(RESOURCES.getString("terminal"), new TerminalCommands(ttyContext, this, tty));
		registry(RESOURCES.getString("debug"), new DebugCommands(ttyContext, this, tty));
	}
	
	public void attach(LineReader reader) {
		this.reader = reader;
		
		new AutosuggestionWidgets(reader).enable();
	}

	public static Path getCwd() {
		return cwd;
	}

	public Object execute(On on, String cmdline) throws Exception {
		systemRegistry.consoleEngine().putVariable(Constants.ACTION_ON_VAR, on);
		return systemRegistry.execute(cmdline);
	}
	
	public void readCommands() {
		systemRegistry.consoleEngine().putVariable(Constants.ACTION_ON_VAR, On.CLI);
		var lastWasCtrlD = false;
		interactive = true;
		try {
			do {
				try {
					if (lastWasCtrlD) {
						try {
							var sb = new AttributedStringBuilder();
							var el = jline.getStringCapability(Capability.cursor_up);
							if (el != null) {
								Curses.tputs(sb, el);
							}
							Curses.tputs(sb, jline.getStringCapability(Capability.carriage_return));
							el = jline.getStringCapability(Capability.clr_eol);
							if (el != null) {
								Curses.tputs(sb, el);
							}
							sb.print(jline);
						} finally {
							lastWasCtrlD = false;
						}
					}
					var rightPrompt = this.rightPrompt;
					this.rightPrompt = null;
					var line = reader.readLine(getPrompt(), rightPrompt, (MaskingCallback) null, null);
					if (line.length() > 0)
						systemRegistry.execute(line);
					

					if(result != null) {
						try {
							var el = jline.getStringCapability(Capability.cursor_up);
							if (el == null) {
								jline.writer().println(result);
							}
							else {
								var sb = new AttributedStringBuilder();
								Curses.tputs(sb, el);
								el = jline.getStringCapability(Capability.cursor_right);
								int w = jline.getWidth() - ( result.length() + 3);
//								Curses.tputs(sb, el, String.valueOf(w)); // TODO why no work?
								for(int i = 0 ; i < w; i++)
									Curses.tputs(sb, el);										
								sb.append("<- ");
								sb.style(new AttributedStyle().faint());
								sb.append(result);
								sb.style(new AttributedStyle().faintOff());
								sb.println(jline);
							}
						}
						finally {
							result = null;
						}
					}

				} catch (UserInterruptException e) {
				} catch (EndOfFileException e) {
					lastWasCtrlD = true;
					runLater(this::close);
				} catch (Exception e) {
					printException(e);
				}
			} while (!canExit.get());
		} finally {
			interactive = false;
			runLater(this::close);
		}
	}
	
	public boolean isInteractive() {
		return interactive;
	}

	public void print(AttributedString message) {
		print(message.toAnsi(jline));
	}
	
	public void print(String message) {
		jline().writer().println(message);
	}
	
	public void printException(Exception e) {
		printException(null, e, null);
	}
	
	public void printError(String message) {
		printException(message, null);
	}
	
	public void printException(String message, Exception e) {
		printException(message, e, null);
	}
	
	@Override
	public abstract void close();

	public TTY tty() {
		return tty;
	}

	public JavaFXTerminalPanel terminal() {
		return terminal;
	}
	
	public void result(String result) {
		result(Styling.styled(result).toAttributedString());
	}
	
	public void result(AttributedString result) {
		this.result = result;
	}
	
	public void rightPrompt(String rightPrompt) {
		this.rightPrompt = rightPrompt;
	}
	
	public Path cwd() {
		return workingDirectory;
	}
	
	public void cwd(Path workingDirectory) {
		this.workingDirectory = workingDirectory;
	}
	
	public Terminal jline() {
		return jline;
	}

	public LineReader reader() {
		return reader;
	}
	
	public void removeCommandRegistry(CommandRegistry registry) {
		this.commandRegistries.remove(registry);
		systemRegistry.setCommandRegistries(this.commandRegistries.toArray(new CommandRegistry[0]));
	}
	
	public void addCommandRegistry(CommandRegistry registry) {
		this.commandRegistries.add(registry);
		systemRegistry.setCommandRegistries(this.commandRegistries.toArray(new CommandRegistry[0]));
	}
	
	private String getPrompt() {
		var proto = tty.protocol(); 
		if(proto == null || proto instanceof ConsoleProtocol)
			return "→ ";
		else
			return proto.displayName() + " → ";
	}

	CommandLine newCommand(Object command) {
		var wtr = jline.writer();

		var cmd = new CommandLine(command, factory);
		cmd.setTrimQuotes(true);
		cmd.setCaseInsensitiveEnumValuesAllowed(true);
		cmd.setExecutionExceptionHandler(exceptionHandler);
		cmd.setOut(wtr);
		cmd.setErr(wtr);
//		cmd.setHelpFactory(new IHelpFactory() {
//			@Override
//			public Help create(CommandSpec commandSpec, ColorScheme colorScheme) {
//				return new Help(commandSpec, colorScheme) {
//			        protected List<String> aliases() { return Collections.emptyList(); }
//				};
//			}
//		});
//		cmd.setUnmatchedArgumentsAllowed(true);
//		cmd.setUnmatchedOptionsAllowedAsOptionParameters(true);
//		cmd.setUnmatchedOptionsArePositionalParams(true);
		return cmd;
	}
	
	public void printException(String message, Exception e, CommandLine cmd) {
		LOG.error("Command failed.", e);
		try {
			if(message == null)
				exceptionHandler.handleExecutionException(e, cmd, null);
			else
				exceptionHandler.printExceptionAndMessage(e, message);
		} catch (Exception e1) {
		}
	}

	public PricliSystemRegistry systemRegistry() {
		return systemRegistry;
	}

	public PricliCommandRegistry registry(String name, Object command) {
		var rgr = new PricliCommandRegistry(newCommand(command));
		rgr.name(name);
		addCommandRegistry(rgr);
		return rgr;
	}

	public Parser parser() {
		return parser;
	}
}
