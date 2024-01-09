package com.sshtools.pretty.pricli;

import static javafx.application.Platform.runLater;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jline.builtins.Completers;
import org.jline.console.CommandRegistry;
import org.jline.console.impl.SystemRegistryImpl;
import org.jline.keymap.KeyMap;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.MaskingCallback;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Curses;
import org.jline.utils.InfoCmp.Capability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.Data.Handle;
import com.sshtools.pretty.ConsoleProtocol;
import com.sshtools.pretty.Constants;
import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TTYContext;
import com.sshtools.pretty.TerminalMenu;
import com.sshtools.terminal.emulation.ResizeStrategy;
import com.sshtools.terminal.emulation.emulator.DECEmulator;
import com.sshtools.terminal.vt.javafx.JavaFXScrollBar;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliCommands.PicocliCommandsFactory;

public final class Pricli {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Pricli.class.getName());
	
	public enum PopupPosition {
		AUTO, TOP, BOTTOM
	}
	
	static final String PRICLI_SECTION = "pricli";
	static Logger LOG = LoggerFactory.getLogger(Pricli.class);
	private static final String SCREEN_TERM_TYPE = "xterm-256color";

	static {
		System.setProperty("picocli.ansi", "true");
	}

	private AtomicBoolean canExit = new AtomicBoolean();
	private BooleanProperty visible = new SimpleBooleanProperty(false);
	private final SystemRegistryImpl systemRegistry;
	private final ExceptionHandler exceptionHandler;
	private final static Path cwd = Paths.get(System.getProperty("user.dir"));
	private final Terminal jline;
	private final LineReader reader;
	private final JavaFXTerminalPanel term;
	private final Region root;
	private final TTY tty;
	private final TTYContext ttyContext;
	private Path workingDirectory = Paths.get(System.getProperty("user.dir"));
	private Thread thread;
	private Pane overlay;
	private AttributedString result;
	private String rightPrompt;
	private final List<Handle> handles = new LinkedList<>();
	private boolean autoAlign = true;
	private List<CommandRegistry> commandRegistries = new ArrayList<>();
	private PicocliCommandsFactory factory;

	public Pricli(TTY tty) {
		this.tty = tty;
		this.ttyContext = tty.getTTYContext();

		var cfg = ttyContext.getContainer().getConfiguration();
		var parser = new DefaultParser(); /* TODO windows / unix path parsing mode */
		var buf = new DECEmulator<JavaFXTerminalPanel>(SCREEN_TERM_TYPE);

		term = new JavaFXTerminalPanel.Builder().
				withUiToolkit(ttyContext.getContainer().getUiToolkit()).
				withFontManager(ttyContext.getContainer().getFonts().getFontManager()).
				withBuffer(buf).
				build();

		var vp = term.getViewport();
		var bd = vp.getPage().data();
		
		term.setResizeStrategy(ResizeStrategy.SCREEN);

//		term.getViewport().setTerminalType(SCREEN_TERM_TYPE);
//		app.getSelectedTheme().aptermterm);
		
		var contextMenu = new TerminalMenu(term, this::hide).menu();

		var ctrl = (Pane) term.getControl();
		ctrl.setOnMousePressed((evt) -> {
			if (evt.isSecondaryButtonDown()) {
				contextMenu.show(ctrl, evt.getScreenX(), evt.getScreenY());
			} else {
				contextMenu.hide();
			}
		});
		
		overlay = new Pane();
		overlay.setBackground(new Background(new BackgroundFill(
				ttyContext.getContainer().getUiToolkit().localColorToNativeColor(term.getViewport().getColors().getBG()),
				CornerRadii.EMPTY, null)));
		overlay.setOnMouseClicked(e->close());
		overlay.setOpacity(0);

		jline = TTY.ttyJLine(SCREEN_TERM_TYPE, vp);
		vp.addResizeListener((trm, cols, rows, remote) -> {
			jline.setSize(new Size(cols, rows));
		});
		
		var root = new BorderPane();
		var scroller = new JavaFXScrollBar(term).getNativeComponent();
		tty.heightProperty().addListener((c,o,n) -> updateHeight());
		root.setRight(scroller);
		root.setCenter(ctrl);
		
		this.root =root;
		autoAlign();
		
		/* Bind to configuration */
		handles.addAll(Arrays.asList(
			cfg.bindEnum(PopupPosition.class, this::setAlign, this::getAlign, "align", PRICLI_SECTION),
			cfg.bindFloat(v -> updateHeight(), () -> -1f, "height", PRICLI_SECTION),
			cfg.bindInteger(vp::setMaximumBufferSize, bd::getMaximumSize, "buffer-size",PRICLI_SECTION),
			cfg.bindBoolean(buf::setEnableScrollback, buf::isEnableScrollback, "scroll-back", PRICLI_SECTION)
		));


		visible.addListener((c, o, n) -> {
			if (n) {
				var children = tty.getChildren();
				tty.terminal().setEventsEnabled(false);
				children.add(overlay);
				children.add(root);
				term.focusTerminal();

				animation(true);
			} else {
				tty.terminal().setEventsEnabled(true);
				tty.getChildren().remove(overlay);
				animation(false).setOnFinished(evt -> {
					tty.getChildren().remove(root);
				});
			}
		});

		term.setOnBeforeKeyTyped(ke -> {
			if (ke.isAltDown() && ke.getCharacter().equals("/")) {
				hide();
				ke.consume();
			}
		});
		
		exceptionHandler = new ExceptionHandler(jline, () -> tty.getTTYContext().getContainer().getConfiguration().getBoolean(Constants.VERBOSE_EXCEPTIONS_KEY,  Constants.DEBUG_SECTION)); 

		factory = new PicocliCommandsFactory();
		var cmd = newCommand(new PricliCommands(ttyContext, this, tty));
		var picocliCommands = new PicocliCommandRegistry(cmd);
		picocliCommands.name(RESOURCES.getString("app"));

		systemRegistry = new SystemRegistryImpl(parser, jline, Pricli::getCwd, null);
		systemRegistry.addCompleter(new Completers.FileNameCompleter());
		addCommandRegistry(picocliCommands);
		registry(RESOURCES.getString("ui"), new UICommands(ttyContext, this, tty));
		registry(RESOURCES.getString("localFileSystem"), new LocalFileSystemCommands(ttyContext, this, tty));
		registry(RESOURCES.getString("connections"), new ConnectionCommands(ttyContext, this, tty));
		registry(RESOURCES.getString("terminal"), new TerminalCommands(ttyContext, this, tty));
		registry(RESOURCES.getString("debug"), new DebugCommands(ttyContext, this, tty));

		reader = LineReaderBuilder.builder().
				completer(systemRegistry.completer()).
				history(new DefaultHistory()).
				parser(parser).
				option(LineReader.Option.DISABLE_EVENT_EXPANSION, true).
				variable(LineReader.LIST_MAX, 50).
				variable(LineReader.HISTORY_FILE, ttyContext.getContainer().getConfiguration().dir().resolve("pretty.history")).
				terminal(jline).build();

//		var widgets = new TailTipWidgets(reader, 
//				systemRegistry::commandDescription, 5,
//				TailTipWidgets.TipType.COMBINED);
//		widgets.enable();

		var keyMap = reader.getKeyMaps().get("main");
		keyMap.bind(new Reference("tailtip-toggle"), KeyMap.alt("s"));

		show();
	}
	
	public void removeCommandRegistry(CommandRegistry registry) {
		this.commandRegistries.remove(registry);
		systemRegistry.setCommandRegistries(this.commandRegistries.toArray(new CommandRegistry[0]));
	}
	
	public void addCommandRegistry(CommandRegistry registry) {
		this.commandRegistries.add(registry);
		systemRegistry.setCommandRegistries(this.commandRegistries.toArray(new CommandRegistry[0]));
	}
	
	public Path cwd() {
		return workingDirectory;
	}
	
	public void cwd(Path workingDirectory) {
		this.workingDirectory = workingDirectory;
	}

	public JavaFXTerminalPanel terminal() {
		return term;
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

	private Animation animation(boolean in) {
		var tt = new TranslateTransition(Duration.millis(125), root);
		tt.setInterpolator(Interpolator.EASE_BOTH);
		
		var ft = new FadeTransition(Duration.millis(125), overlay);
		ft.setInterpolator(Interpolator.EASE_BOTH);
		
		var ct = new FadeTransition(Duration.millis(125), root);
		ct.setInterpolator(Interpolator.EASE_BOTH);
		
		if(in) {
			ft.setFromValue(0);
			ft.setToValue(0.5);
			ct.setFromValue(0);
			ct.setToValue(0.9);
			if (StackPane.getAlignment(root) == Pos.TOP_CENTER) {
				tt.setToY(0);
				tt.setFromY(-root.getHeight());
			} else {
				tt.setToY(0);
				tt.setFromY(root.getHeight());
			}
		}
		else {
			ft.setToValue(0);
			ft.setFromValue(0.5);
			ct.setToValue(0);
			ct.setFromValue(0.9);
			if (StackPane.getAlignment(root) == Pos.TOP_CENTER) {
				tt.setFromY(0);
				tt.setToY(-root.getHeight());
			} else {
				tt.setFromY(0);
				tt.setToY(root.getHeight());
			}
		}
		
		var pt = new ParallelTransition(tt, ft, ct);
		pt.play();
		
		return pt;
	}
	
	public TTY tty() {
		return tty;
	}

	public void close() {
		handles.forEach(Handle::close);
		Platform.runLater(this::hide);
	}

	public Terminal jline() {
		return jline;
	}

	public void hide() {
		visible.set(false);
		tty.terminal().focusTerminal();
	}

	public boolean showing() {
		return visible.get();
	}

	public void show() {
		startReader();
		visible.set(true);
	}

	public void execute(String cmdline) {
		try {
			systemRegistry.execute(cmdline);
		}
		catch(Exception e) {
			printException(e);
		}
		catch(Throwable t) {
			LOG.error("Uncatchable error.", t);
		}
	}
	
	private String getPrompt() {
		var proto = tty.protocol(); 
		if(proto instanceof ConsoleProtocol)
			return "→ ";
		else
			return proto.displayName() + " → ";
	}
	
	private void readCommands() {
		var lastWasCtrlD = false;
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
					runLater(this::hide);
				} catch (Exception e) {
					printException(e);
				}
			} while (!canExit.get());
		} finally {
			runLater(this::hide);
		}
	}

	private void startReader() {
		if (thread == null || !thread.isAlive()) {
			systemRegistry.cleanUp();
			thread = new Thread(this::readCommands, "PricliPrompt");
			thread.start();
		}
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

	public LineReader reader() {
		return reader;
	}

	public static Path getCwd() {
		return cwd;
	}
	
	public PicocliCommandRegistry registry(String name, Object command) {
		var registry = new PicocliCommandRegistry(newCommand(command));
		registry.name(name);
		addCommandRegistry(registry);
		return registry;
	}
	
	private void updateHeight() {
		var hf = ttyContext.getContainer().getConfiguration().getFloat("height", PRICLI_SECTION);
		if(hf == 0)
			hf = 0.5f;
		
		if(hf < 1) {
			root.maxHeightProperty().set(tty.heightProperty().get() * hf);			
		}
		else {
			root.maxHeightProperty().set((int)hf * term.getFontManager().getDefault().charHeight());
		}
	}

	private void setAlign(PopupPosition p) {
		switch(p) {
		case TOP:
			autoAlign = false;
			StackPane.setAlignment(root, Pos.TOP_CENTER);
			break;
		case BOTTOM:
			autoAlign = false;
			StackPane.setAlignment(root, Pos.BOTTOM_CENTER);
			break;
		case AUTO:
			autoAlign  = true;
			autoAlign();
			break;
		}
	}

	private void autoAlign() {
		if (tty.terminal().getViewport().getPage().cursorY() < tty.terminal().getViewport().getRows()) {
			StackPane.setAlignment(root, Pos.BOTTOM_CENTER);
		} else {
			StackPane.setAlignment(root, Pos.TOP_CENTER);
		}
	}

	private PopupPosition getAlign() {
		if(autoAlign) {
			return PopupPosition.AUTO;
		}
		else {
			if(StackPane.getAlignment(root) == Pos.TOP_CENTER) {
				return PopupPosition.TOP;
			}
			else {
				return PopupPosition.BOTTOM;
			}
		}
	}

	private CommandLine newCommand(Object command) {
		var wtr = jline.writer();

		var cmd = new CommandLine(command, factory);
		cmd.setTrimQuotes(true);
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

}
