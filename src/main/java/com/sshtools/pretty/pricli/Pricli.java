package com.sshtools.pretty.pricli;

import static javafx.application.Platform.runLater;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.jline.builtins.Completers;
import org.jline.builtins.Options.HelpException;
import org.jline.console.ArgDesc;
import org.jline.console.CmdDesc;
import org.jline.console.CommandRegistry;
import org.jline.console.impl.SystemRegistryImpl;
import org.jline.keymap.KeyMap;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.MaskingCallback;
import org.jline.reader.ParsedLine;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.SystemCompleter;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Curses;
import org.jline.utils.InfoCmp.Capability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.Data.Handle;
import com.sshtools.pretty.TTY;
import com.sshtools.pretty.TTYContext;
import com.sshtools.pretty.TerminalMenu;
import com.sshtools.terminal.emulation.ResizeStrategy;
import com.sshtools.terminal.emulation.TerminalInputStream;
import com.sshtools.terminal.emulation.TerminalOutputStream;
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
import picocli.CommandLine.Command;
import picocli.CommandLine.Help;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.shell.jline3.PicocliCommands.ClearScreen;
import picocli.shell.jline3.PicocliCommands.PicocliCommandsFactory;

public final class Pricli {
	
	public enum PopupPosition {
		AUTO, TOP, BOTTOM
	}
	
	private static final String PRICLI_SECTION = "pricli";
	static Logger LOG = LoggerFactory.getLogger(Pricli.class);
	private static final String SCREEN_TERM_TYPE = "xterm-256color";

	static {
		System.setProperty("picocli.ansi", "true");
	}

	private AtomicBoolean canExit = new AtomicBoolean();
	private BooleanProperty visible = new SimpleBooleanProperty(false);
	private final SystemRegistryImpl systemRegistry;
	private final ExceptionHandler exceptionHandler;
	private final CommandLine cmd;
	private final static Path cwd = Paths.get(System.getProperty("user.dir"));
	private final Terminal jline;
	private final LineReader reader;
	private final JavaFXTerminalPanel term;
	private final Region root;
	private final TTY tty;
	private final TTYContext ttyContext;

	private Thread thread;
	private Pane overlay;
	private AttributedString result;
	private String rightPrompt;

	private final List<Handle> handles = new LinkedList<>();
	private boolean autoAlign = true;

	public Pricli(TTY tty) {
		this.tty = tty;
		this.ttyContext = tty.getTTYContext();

		var cfg = ttyContext.getContainer().getConfiguration();
		var parser = new DefaultParser(); /* TODO windows / unix path parsing mode */
		var buf = new DECEmulator<JavaFXTerminalPanel>(SCREEN_TERM_TYPE);

		term = new JavaFXTerminalPanel.Builder().
				withUiToolkit(ttyContext.getContainer().getUiToolkit()).
				withFontManager(ttyContext.getContainer().getFontManager()).
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
				ttyContext.getContainer().getUiToolkit().localColorToNativeColor(term.getDefaultBackground()),
				CornerRadii.EMPTY, null)));
		overlay.setOnMouseClicked(e->close());
		overlay.setOpacity(0);

		try {
			var sz = new Size(vp.getColumns(), vp.getRows());
			LOG.info("Initial shell size to {} x {}", sz.getColumns(), sz.getRows());
			jline = TerminalBuilder.builder().
					type(SCREEN_TERM_TYPE).
					size(sz).
					streams(new TerminalInputStream(vp), 
							new TerminalOutputStream(vp)
					).
					build();

			vp.addResizeListener((trm, cols, rows, remote) -> {
				LOG.info("Shell size change to {} x {}", cols, rows);
				jline.setSize(new Size(cols, rows));
			});
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
		
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

		var factory = new PicocliCommandsFactory();
		var wtr = jline.writer();

		cmd = new CommandLine(new PricliCommands(ttyContext, this, tty), factory);
		cmd.setTrimQuotes(true);
		exceptionHandler = new ExceptionHandler(jline, false);
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

		var picocliCommands = new PicocliCommands(cmd);
		picocliCommands.name("Terminal");

		systemRegistry = new SystemRegistryImpl(parser, jline, Pricli::getCwd, null);
		systemRegistry.setCommandRegistries(picocliCommands);
		systemRegistry.addCompleter(new Completers.FileNameCompleter());

		reader = LineReaderBuilder.builder().
				completer(systemRegistry.completer()).
				parser(parser).
				option(LineReader.Option.DISABLE_EVENT_EXPANSION, true).
				variable(LineReader.LIST_MAX, 50).
				terminal(jline).build();

//		var widgets = new TailTipWidgets(screen.reader(), 
//				systemRegistry::commandDescription, 5,
//				TailTipWidgets.TipType.COMBINED);
//		widgets.enable();

		var keyMap = reader.getKeyMaps().get("main");
		keyMap.bind(new Reference("tailtip-toggle"), KeyMap.alt("s"));

		show();
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

	public void show() {
		startReader();
		visible.set(true);
	}

	private void startReader() {
		if (thread == null || !thread.isAlive()) {
			systemRegistry.cleanUp();
			thread = new Thread(() -> {
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
							var line = reader.readLine("-> ", rightPrompt, (MaskingCallback) null, null);
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
//										Curses.tputs(sb, el, String.valueOf(w)); // TODO why no work?
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
			}, "PricliPrompt");
			thread.start();
		}

	}

	public void printException(Exception e) {
		LOG.error("Command failed.", e);
		try {
			exceptionHandler.handleExecutionException(e, cmd, null);
		} catch (Exception e1) {
		}
	}

	public LineReader reader() {
		return reader;
	}

	public static Path getCwd() {
		return cwd;
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

	@Command(name = PRICLI_SECTION, description = "Pricli, (or 'Pretty Interactive Command Line Interface')", 
			subcommands = {
			About.class, Record.class, Cd.class, SelectAll.class, ClearScreen.class, Stop.class, Reset.class,
			Serial.class, Ssh.class, SetConfig.class, Show.class, Copy.class, Quit.class, Features.class, Images.class, Fonts.class, Charmap.class, Themes.class,
					Debug.class, Console.class, NewTab.class, NewWindow.class/* , HelpCommand.class */ })
	public final static class PricliCommands implements Callable<Integer> {

		private final TTYContext ttyContext;
		private final Pricli screen;
		private final TTY tty;

		private PricliCommands(TTYContext ttyContext, Pricli screen, TTY tty) {
			this.ttyContext = ttyContext;
			this.screen = screen;
			this.tty = tty;
		}

		public Pricli cli() {
			return screen;
		}

		public TTY tty() {
			return tty;
		}

		public TTYContext ttyContext() {
			return ttyContext;
		}

		@Override
		public Integer call() throws Exception {
			return 0;
		}

	}
	
	/**
	 * Compiles SystemCompleter for command completion and implements a method commandDescription() that provides command descriptions
	 * for JLine TailTipWidgets to be displayed in terminal status bar.
	 * SystemCompleter implements the JLine 3 {@link Completer} interface. SystemCompleter generates completion
	 * candidates for the specified command line based on the {@link CommandSpec} that this {@code PicocliCommands} was constructed with.
	 *
	 * @since 4.1.2
	 */
	public final static class PicocliCommands implements CommandRegistry {
	    private String picocliCommandsName;

	    /**
	     * Command that clears the screen.
	     * <p>
	     * <b>WARNING:</b> This subcommand needs a JLine {@code Terminal} to clear the screen.
	     * To accomplish this, construct the {@code CommandLine} with a {@code PicocliCommandsFactory},
	     * and set the {@code Terminal} on that factory. For example:
	     * <pre>
	     * &#064;Command(subcommands = PicocliCommands.ClearScreen.class)
	     * class MyApp //...
	     *
	     * PicocliCommandsFactory factory = new PicocliCommandsFactory();
	     * CommandLine cmd = new CommandLine(new MyApp(), factory);
	     * // create terminal
	     * factory.setTerminal(terminal);
	     * </pre>
	     *
	     * @since 4.6
	     */
	    @Command(name = "cls", aliases = "clear", mixinStandardHelpOptions = true,
	            description = "Clears the screen", version = "1.0")
	    public static class ClearScreen implements Callable<Void> {

	        private final Terminal terminal;

	        ClearScreen(Terminal terminal) { this.terminal = terminal; }

	        public Void call() throws IOException {
	            if (terminal != null) { terminal.puts(Capability.clear_screen); }
	            return null;
	        }
	    }

	    /**
	     * Command factory that is necessary for applications that want the use the {@code ClearScreen} subcommand.
	     * It can be chained with other factories.
	     * <p>
	     * <b>WARNING:</b> If the application uses the {@code ClearScreen} subcommand,  construct the {@code CommandLine}
	     * with a {@code PicocliCommandsFactory}, and set the {@code Terminal} on that factory. Applications need
	     * to call the {@code setTerminal} method with a {@code Terminal}; this will be passed to the {@code ClearScreen}
	     * subcommand.
	     *
	     * For example:
	     * <pre>
	     * PicocliCommandsFactory factory = new PicocliCommandsFactory();
	     * CommandLine cmd = new CommandLine(new MyApp(), factory);
	     * // create terminal
	     * factory.setTerminal(terminal);
	     * </pre>
	     *
	     * Other factories can be chained by passing them in to the constructor like this:
	     * <pre>
	     * MyCustomFactory customFactory = createCustomFactory(); // your application custom factory
	     * PicocliCommandsFactory factory = new PicocliCommandsFactory(customFactory); // chain the factories
	     * </pre>
	     *
	     * @since 4.6
	     */
	    public static class PicocliCommandsFactory implements CommandLine.IFactory {
	        private CommandLine.IFactory nextFactory;
	        private Terminal terminal;

	        public PicocliCommandsFactory() {
	            // nextFactory and terminal are null
	        }

	        public PicocliCommandsFactory(IFactory nextFactory) {
	            this.nextFactory = nextFactory;
	            // nextFactory is set (but may be null) and terminal is null
	        }

	        @SuppressWarnings("unchecked")
	        public <K> K create(Class<K> clazz) throws Exception {
	            if (ClearScreen.class == clazz) { return (K) new ClearScreen(terminal); }
	            if (nextFactory != null) { return nextFactory.create(clazz); }
	            return CommandLine.defaultFactory().create(clazz);
	        }

	        public void setTerminal(Terminal terminal) {
	            this.terminal = terminal;
	            // terminal may be null, so check before using it in ClearScreen command
	        }
	    }

	    private final CommandLine cmd;
	    private final Set<String> commands;
	    private final Map<String,String> aliasCommand = new HashMap<>();

	    public PicocliCommands(CommandLine cmd) {
	        this.cmd = cmd;
	        commands = cmd.getCommandSpec().subcommands().keySet();
//	        for (String c: commands) {
//	            for (String a: cmd.getSubcommands().get(c).getCommandSpec().aliases()) {
//	                aliasCommand.put(a, c);
//	            }
//	        }
	    }

	    /**
	     *
	     * @param command
	     * @return true if PicocliCommands contains command
	     */
	    public boolean hasCommand(String command) {
	        return commands.contains(command) || aliasCommand.containsKey(command);
	    }


	    public SystemCompleter compileCompleters() {
	        SystemCompleter out = new SystemCompleter();
	        List<String> all = new ArrayList<>();
	        all.addAll(commands);
	        all.addAll(aliasCommand.keySet());
	        out.add(all, new PicocliCompleter());
	        return out;
	    }

	    private class PicocliCompleter extends ArgumentCompleter implements Completer {

	        public PicocliCompleter() { super(NullCompleter.INSTANCE); }

	        @Override
	        public void complete(LineReader reader, ParsedLine commandLine, List<Candidate> candidates) {
	            assert commandLine != null;
	            assert candidates != null;
	            String word = commandLine.word();
	            List<String> words = commandLine.words();
	            CommandLine sub = findSubcommandLine(words, commandLine.wordIndex());
	            if (sub == null) {
	                return;
	            }
	            if (word.startsWith("-")) {
	                String buffer = word.substring(0, commandLine.wordCursor());
	                int eq = buffer.indexOf('=');
	                for (OptionSpec option : sub.getCommandSpec().options()) {
	                    if (option.hidden()) continue;
	                    if (option.arity().max() == 0 && eq < 0) {
	                        addCandidates(candidates, Arrays.asList(option.names()));
	                    } else {
	                        if (eq > 0) {
	                            String opt = buffer.substring(0, eq);
	                            if (Arrays.asList(option.names()).contains(opt) && option.completionCandidates() != null) {
	                                addCandidates(candidates, option.completionCandidates(), buffer.substring(0, eq + 1), "", true);
	                            }
	                        } else {
	                            addCandidates(candidates, Arrays.asList(option.names()), "", "=", false);
	                        }
	                    }
	                }
	            } else {
	                addCandidates(candidates, sub.getSubcommands().keySet());
	                for (CommandLine s : sub.getSubcommands().values()) {
	                    if (!s.getCommandSpec().usageMessage().hidden()) {
	                        addCandidates(candidates, Arrays.asList(s.getCommandSpec().aliases()));
	                    }
	                }
	            }
	        }

	        private void addCandidates(List<Candidate> candidates, Iterable<String> cands) {
	            addCandidates(candidates, cands, "", "", true);
	        }

	        private void addCandidates(List<Candidate> candidates, Iterable<String> cands, String preFix, String postFix, boolean complete) {
	            for (String s : cands) {
	                candidates.add(new Candidate(AttributedString.stripAnsi(preFix + s + postFix), s, null, null, null, null, complete));
	            }
	        }

	    }

	    private CommandLine findSubcommandLine(List<String> args, int lastIdx) {
	        CommandLine out = cmd;
	        for (int i = 0; i < lastIdx; i++) {
	            if (!args.get(i).startsWith("-")) {
	                out = findSubcommandLine(out, args.get(i));
	                if (out == null) {
	                    break;
	                }
	            }
	        }
	        return out;
	    }

	    private CommandLine findSubcommandLine(CommandLine cmdline, String command) {
	        for (CommandLine s : cmdline.getSubcommands().values()) {
	            if (s.getCommandName().equals(command) || Arrays.asList(s.getCommandSpec().aliases()).contains(command)) {
	                return s;
	            }
	        }
	        return null;
	    }

	    /**
	     *
	     * @param args
	     * @return command description for JLine TailTipWidgets to be displayed in terminal status bar.
	     */
	    @Override
	    public CmdDesc commandDescription(List<String> args) {
	        CommandLine sub = findSubcommandLine(args, args.size());
	        if (sub == null) {
	            return null;
	        }
	        CommandSpec spec = sub.getCommandSpec();
	        Help cmdhelp= new picocli.CommandLine.Help(spec);
	        List<AttributedString> main = new ArrayList<>();
	        Map<String, List<AttributedString>> options = new HashMap<>();
	        String synopsis = AttributedString.stripAnsi(spec.usageMessage().sectionMap().get("synopsis").render(cmdhelp).toString());
	        main.add(HelpException.highlightSyntax(synopsis.trim(), HelpException.defaultStyle()));
	        // using JLine help highlight because the statement below does not work well...
	        //        main.add(new AttributedString(spec.usageMessage().sectionMap().get("synopsis").render(cmdhelp).toString()));
	        for (OptionSpec o : spec.options()) {
	            String key = Arrays.stream(o.names()).collect(Collectors.joining(" "));
	            List<AttributedString> val = new ArrayList<>();
	            for (String d:  o.description()) {
	                val.add(new AttributedString(d));
	            }
	            if (o.arity().max() > 0) {
	                key += "=" + o.paramLabel();
	            }
	            options.put(key, val);
	        }
	        return new CmdDesc(main, ArgDesc.doArgNames(Arrays.asList("")), options);
	    }

	    @Override
	    public List<String> commandInfo(String command) {
	        List<String> out = new ArrayList<>();
	        CommandSpec spec = cmd.getSubcommands().get(command).getCommandSpec();
	        Help cmdhelp = new picocli.CommandLine.Help(spec);
	        String description = AttributedString.stripAnsi(spec.usageMessage().sectionMap().get("description").render(cmdhelp).toString());
	        out.addAll(Arrays.asList(description.split("\\r?\\n")));
	        return out;
	    }

		@Override
		public Object invoke(CommandRegistry.CommandSession session, String command, Object[] args)
				throws Exception {
			var arguments = new ArrayList<String>();
			arguments.add(command);
			arguments.addAll(Arrays.stream(args).map(Object::toString).toList());
			return cmd.execute(arguments.toArray(new String[0]));
		}

	    // @Override This method was removed in JLine 3.16.0; keep it in case this component is used with an older version of JLine
	    public Object execute(CommandRegistry.CommandSession session, String command, String[] args) throws Exception {
	        List<String> arguments = new ArrayList<>();
	        arguments.add(command);
	        arguments.addAll(Arrays.asList(args));
	        cmd.execute(arguments.toArray(new String[0]));
	        return null;
	    }

	    @Override
	    public Set<String> commandNames() {
	        return commands;
	    }

	    @Override
	    public Map<String, String> commandAliases() {
	        return aliasCommand;
	    }

	    // @Override This method was removed in JLine 3.16.0; keep it in case this component is used with an older version of JLine
	    public CmdDesc commandDescription(String command) {
	        return null;
	    }

	    /**
	     * Returns the name shown for this collection of picocli commands in the usage help message.
	     * If not set with {@link #name(String)}, this returns {@link CommandRegistry#name()}.
	     * @return the name shown for this collection of picocli commands in the usage help message
	     */
	    @Override
	    public String name() {
	        if (picocliCommandsName != null) {
	            return picocliCommandsName;
	        }
	        return CommandRegistry.super.name();
	    }

	    /**
	     * Sets the name shown for this collection of picocli commands in the usage help message.
	     * @param newName the new name to show
	     */
	    public void name(String newName) {
	        picocliCommandsName = newName;
	    }
	}

}
