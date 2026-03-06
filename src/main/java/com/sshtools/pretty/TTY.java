package com.sshtools.pretty;

import static com.sshtools.jajafx.FXUtil.maybeQueue;
import static javafx.application.Platform.runLater;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Stack;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jajafx.FXUtil;
import com.sshtools.jini.Data.Handle;
import com.sshtools.pretty.Actions.On;
import com.sshtools.pretty.Shells.Shell;
import com.sshtools.pretty.Shells.ShellType;
import com.sshtools.pretty.pricli.PricliPopup;
import com.sshtools.pretty.pricli.PricliProtocol;
import com.sshtools.terminal.emulation.CursorStyle;
import com.sshtools.terminal.emulation.Feature;
import com.sshtools.terminal.emulation.ResizeStrategy;
import com.sshtools.terminal.emulation.TerminalInputStream;
import com.sshtools.terminal.emulation.TerminalOutputStream;
import com.sshtools.terminal.emulation.TerminalViewport;
import com.sshtools.terminal.emulation.buffer.FixedSizeInMemoryBufferData;
import com.sshtools.terminal.emulation.buffer.ScrollBackBufferData.Mode;
import com.sshtools.terminal.emulation.emulator.DECEmulator;
import com.sshtools.terminal.emulation.emulator.DECModes;
import com.sshtools.terminal.emulation.emulator.DECModes.StatusLineType;
import com.sshtools.terminal.emulation.emulator.XTERM256Color;
import com.sshtools.terminal.emulation.events.ViewportEvent;
import com.sshtools.terminal.emulation.events.ViewportListener;
import com.sshtools.terminal.emulation.fonts.FontSpec;
import com.sshtools.terminal.emulation.util.Cell;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Transition;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.Resource;

@Bundle
@Resource
@Reflectable(all = true)
public class TTY extends StackPane implements Closeable {
	
	public final static class Builder {
		private TTYContext ttyContext;
		private Optional<Consumer<TTY>> onClose = Optional.empty();
		private Optional<TTYRequest> request = Optional.empty();
		
		public Builder(TTYContext ttyContext) {
			this.ttyContext = ttyContext;
		}
		
		public TTY build() {
			return new TTY(this);
		}
		
		public Builder onClose(Consumer<TTY> onClose) {
			this.onClose = Optional.of(onClose);
			return this;
		}

		public Builder withRequest(TTYRequest request) {
			this.request = Optional.of(request);
			return this;
		}
		
		public Builder withShell(Shell shell) {
			return withRequest(new TTYRequest.Builder().withShell(shell).build());
		}
	}
	
	private final static Map<String, Integer> counters = Collections.synchronizedMap(new HashMap<String, Integer>());

	static Logger LOG = LoggerFactory.getLogger(TTY.class);

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(TTY.class.getName());

	private final static AtomicInteger counter = new AtomicInteger(1);

	public static Terminal ttyJLine(String termType, TerminalViewport<JavaFXTerminalPanel, ?, ?> vp) {
		try {
			var sz = new Size(vp.getColumns(), vp.getRows());
			LOG.info("Initial shell size to {} x {} # {}", sz.getColumns(), sz.getRows(), termType);
			var jline = TerminalBuilder.builder().
					type(termType).
					size(sz).
					streams(new TerminalInputStream(vp), 
							new TerminalOutputStream(vp)
					).
					build();
			
			return jline;
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}
	private static int nextVirtualConsoleNumber(String shell) {
		synchronized(counters) {
			var i = counters.get(shell);
			if(i == null) {
				i = 0;
			}
			else {
				i = i + 1;
			}
			counters.put(shell, i);
			return i;
		}
	}
	private final JavaFXTerminalPanel terminalPanel;

	private final TTYContext ttyContext;
	private final List<Handle> handles = new LinkedList<>();
	private final Stack<TerminalProtocol> protocols = new Stack<>();
	private final StringProperty title = new SimpleStringProperty(RESOURCES.getString("title"));
	private final StringProperty shortTitle = new SimpleStringProperty(RESOURCES.getString("appType"));
	private final StringProperty userTitle = new SimpleStringProperty("");
	private final ObjectProperty<Color> tabColor = new SimpleObjectProperty<>();
	private final Optional<Consumer<TTY>> onClose;
	private final Status status = new Status();
	
	private PricliPopup pricli;
	private TerminalTheme theme;
	private ScrollableTerminal scrollPane;
	private JavaFXTerminalPanel statusTerminal;
	private boolean closed;
	private Transition resizeFade;
	private Path cwd;
	private Label overlayInfo;

	private DECEmulator<JavaFXTerminalPanel> statusEmulator;
	
	@SuppressWarnings("unused")
	private TTY(Builder bldr) {
		this.ttyContext = bldr.ttyContext;
		this.onClose = bldr.onClose;
		this.cwd = Paths.get(System.getProperty("user.dir"));
		theme = ttyContext.getContainer().getSelectedTheme();
		
		var cfg = ttyContext.getContainer().getConfiguration();

		/* UI */
		var loader = new FXMLLoader(getClass().getResource("TTY.fxml"));
		loader.setController(this);
		loader.setRoot(this);
		try {
			loader.load();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		/* Styles */
		var ss = getStylesheets();
		ss.add(TTY.class.getResource("TTY.css").toExternalForm());

		/* Emulator */
		var sz = getConfiguredSize();
		var emulator = new DECEmulator<JavaFXTerminalPanel>(XTERM256Color.ID, sz[0], sz[1]);

		/* Create and configure terminal */
		terminalPanel = new JavaFXTerminalPanel.Builder().
				withAudioSystem(new TTYAudioSystem(this)).
				withUiToolkit(ttyContext.getContainer().getUiToolkit()).
				withFontManager(ttyContext.getContainer().getFonts().getFontManager()).
				withBuffer(emulator)
				.build();
		terminalPanel.getViewport().addViewportListener(new ViewportListener() {
			@Override
			public void selectionChanged(Cell start, Cell end, Cell point, ViewportEvent event) {
				if(terminalPanel.isSetClipboardOnSelect() && emulator.getSelectionLength() > 0 &&  event.isMajorChange()) {
					Platform.runLater(() ->showOverlayTextInfo(overlayInfo, RESOURCES.getString("copied"))); 
				}
			}
		});
		theme.apply(terminalPanel, getBackgroundOpacity());

		/* Overlay status */
		overlayInfo = new Label("         ");
		overlayInfo.setVisible(false);
		overlayInfo.managedProperty().bind(overlayInfo.visibleProperty());
		overlayInfo.getStyleClass().add("overlay-info");
		
		/* Configure terminal's buffer */
		emulator.addCWDChangeListener((vp, cwd) -> {
			protocol().cwd(cwd);
		});
		emulator.getTransferManager().addTransferListener(new TransferHandler(this));
		emulator.addTitleChangeListener((t, title) -> runLater(() -> this.title.setValue(title)));
		emulator.setNotifications(new TwoSlicesNotificattions());
		emulator.addResizeListener((terminal, columns, rows, remote) -> {
			updateStatusSize();
			if (remote) {
				runLater(() -> {
					if (terminalPanel.getResizeStrategy() == ResizeStrategy.SCREEN) {
						LOG.info("Sizing to scene");
						getStage().sizeToScene();
					}
				});
			}
			runLater(() -> showOverlayTextInfo(overlayInfo, String.format("%d x %d", terminalPanel.getViewport().getColumns(), terminalPanel.getViewport().getRows())));
		});
		emulator.addModeChangeListener(modes -> {
			if(statusTerminal != null)
				statusTerminal.getViewport().enqueue(() ->checkStatusDisplay());
		});

		/* Scrolling */
		scrollPane = new ScrollableTerminal(
			ttyContext, 
			terminalPanel, 
			new ActionsContextMenu(On.CONTEXT, ttyContext.getContainer().getActions(), () -> cli().shell()), 
			() -> cli().shell(),
			On.TTY
		);

		/* Build this stack */
		getChildren().add(scrollPane);
		getChildren().add(overlayInfo);
		
		userTitle.addListener((c,o,n) -> updateState());
		tabColor.addListener((c,o,n) -> updateState());
		
		/* Status */
		status.add(new Status.SizeAndCursor(this));
		status.add(new Status.InsertReplaceMode(this));
			
		var statusHeight = cfg.status().getInt(Constants.HEIGHT_KEY);
		statusEmulator = new DECEmulator<JavaFXTerminalPanel>(
			"StatusLine",
			terminalPanel.getViewport().getTerminalType(), 
			new FixedSizeInMemoryBufferData(statusHeight),
			sz[0], 
			statusHeight
		);
			
		statusTerminal = new JavaFXTerminalPanel.Builder().
				withUiToolkit(ttyContext.getContainer().getUiToolkit()).
				withFontManager(ttyContext.getContainer().getFonts().getFontManager()).
				withBuffer(statusEmulator)
				.build();
		statusTerminal.setResizeStrategy(ResizeStrategy.SCREEN);
		statusTerminal.setEventsEnabled(false);
		statusEmulator.addResizeListener((term,cols,rows,remote) -> checkStatusDisplay());
		statusEmulator.getModes().setShowCursor(false);
		
		var statusControl = statusTerminal.getControl();
		statusControl.setVisible(false);
		statusControl.getStyleClass().add("status-display");
		statusControl.managedProperty().bind(statusControl.visibleProperty());
		terminalPanel.getViewport().setStatusLineDisplay(statusTerminal);
		
		/* Set the height again. TC sets the height based on terminal type, we overide
		 * it and base it on configuration
		 */
		updateStatusSize();

		/* Bind to configuration */
		handles.addAll(Arrays.asList(
				/* TODO some need binding */
//				cfg.bindEnum(ResizeStrategy.class, this::setResizeStrategy, terminalPanel::getResizeStrategy, "resize-strategy", Options.TERMINAL_SECTION),
//				cfg.bindInteger(buf::setMaximumSize, buf::getMaximumSize, "buffer-size", Options.TERMINAL_SECTION),
//				cfg.bindString(emulator::setTerminalType, emulator.getTerminalType()::getId, "type", Options.TERMINAL_SECTION),  
//				cfg.bindString((nsz) -> updateAppearance(), () -> String.format("%dx%d", emulator.getColumns(), emulator.getRows()), "screen-size", Options.TERMINAL_SECTION),
//				cfg.bindInteger(this::setFontSize, terminalPanel.getFontManager().getDefault().spec()::getSize, "font-size", Options.TERMINAL_SECTION),
//				cfg.bindStrings(this::setFonts, this::getFonts, "fonts", Options.TERMINAL_SECTION),
//				cfg.bindStrings((s) -> updateFeatures(), this::getEnabledFeatures, "enabled-features", Options.TERMINAL_SECTION), 
//				cfg.bindStrings((s) -> updateFeatures(), this::getDisabledFeatures, "disabled-features", Options.TERMINAL_SECTION),
//				cfg.bindString(this::setThemeName, this::getThemeName, Constants.THEME_KEY, Options.TERMINAL_SECTION),
//				cfg.bindBoolean(this::setStatusDisplay, this::isStatusDisplay, "enabled", STATUS_SECTION),
//				cfg.bindBoolean(emulator::setEnableScrollback, emulator::isEnableScrollback, "scroll-back", Options.TERMINAL_SECTION),
//				cfg.bindBoolean(emulator::setEnableBlinking, emulator::isEnableBlinking, "blinking", Options.TERMINAL_SECTION),
//				cfg.bindBoolean(emulator.getModes()::setCursorBlink, emulator.getModes()::isCursorBlink, "cursor-blink", Options.TERMINAL_SECTION),
//				cfg.bindEnum(CursorStyle.class, terminalPanel::setCursorStyle, terminalPanel::getCursorStyle, "cursor-style", Options.TERMINAL_SECTION)
		));
		
		scrollPane.setBottom(statusControl);
//			statusTerminal.getControl().setVisible(true);
//			statusTerminal.getControl().setManaged(true);

		/* Setup */
		updateAppearance();
		updateState();

		/* Bind to configuration */
		handles.addAll(Arrays.asList(
			cfg.bindEnum(ResizeStrategy.class, this::setResizeStrategy, terminalPanel::getResizeStrategy, Constants.RESIZE_STRATEGY_KEY, Constants.TERMINAL_SECTION),
			cfg.bindInteger(s -> { 
						emulator.setScrollbackSize(cfg.terminal().getBoolean(Constants.LIMIT_BUFFER_KEY) ? s : -1);
						scrollPane.setScrollBar(cfg.terminal().getEnum(ScrollBarMode.class, Constants.SCROLL_BAR_KEY));
					}, 
					emulator::getScrollbackSize, 
					Constants.BUFFER_SIZE_KEY, Constants.TERMINAL_SECTION),
			cfg.bindBoolean(s -> emulator.setScrollbackSize(s ? cfg.terminal().getInt(Constants.BUFFER_SIZE_KEY): -1), () -> emulator.getScrollbackSize() > -1, Constants.LIMIT_BUFFER_KEY, Constants.TERMINAL_SECTION),
			cfg.bindInteger(this::setBackgroundOpacity, this::getBackgroundOpacity, Constants.OPACITY_KEY, Constants.TERMINAL_SECTION),
			cfg.bindString(emulator::setTerminalType, emulator.getTerminalType()::getId, Constants.TYPE_KEY, Constants.TERMINAL_SECTION),  
//			cfg.bindString((nsz) -> updateAppearance(), () -> String.format("%dx%d", emulator.getColumns(), emulator.getRows()), Constants.SCREEN_SIZE_KEY, Constants.TERMINAL_SECTION),
			cfg.bindInteger(this::setFontSize, terminalPanel.getFontManager().getDefault().spec()::getSize, Constants.FONT_SIZE_KEY, Constants.TERMINAL_SECTION),
			cfg.bindStrings(this::setFonts, ttyContext.getContainer().getFonts()::getFonts, Constants.FONTS_KEY, Constants.TERMINAL_SECTION),
			cfg.bindStrings((s) -> updateFeatures(), this::getEnabledFeatures, Constants.ENABLED_FEATURES, Constants.TERMINAL_SECTION), 
			cfg.bindStrings((s) -> updateFeatures(), this::getDisabledFeatures, Constants.DISABLED_FEATURES, Constants.TERMINAL_SECTION),
			cfg.bindString(this::setThemeId, this::getThemeId, Constants.THEME_KEY, Constants.TERMINAL_SECTION),
			cfg.bindBoolean((s) -> emulator.enqueue(() -> checkStatusDisplay()), this::isStatusDisplay, Constants.ENABLED_KEY, Constants.STATUS_SECTION),
			cfg.bindBoolean(emulator::setEnableBlinking, emulator::isEnableBlinking, Constants.BLINKING_KEY, Constants.TERMINAL_SECTION),
			cfg.bindBoolean(emulator::setReflow, emulator::isReflow, Constants.REFLOW_KEY, Constants.TERMINAL_SECTION),
			cfg.bindBoolean(terminalPanel::setSetClipboardOnSelect, terminalPanel::isSetClipboardOnSelect, Constants.COPY_ON_SELECT, Constants.TERMINAL_SECTION),
			cfg.bindBoolean(emulator.getModes()::setCursorBlink, emulator.getModes()::isCursorBlink, Constants.CURSOR_BLINK_KEY, Constants.TERMINAL_SECTION),
			cfg.bindEnum(Mode.class, emulator::setScrollbackMode, emulator::getScrollbackMode, Constants.BUFFER_MODE_KEY, Constants.TERMINAL_SECTION),
			cfg.bindEnum(CursorStyle.class, terminalPanel::setCursorStyle, terminalPanel::getCursorStyle, Constants.CURSOR_STYLE_KEY, Constants.TERMINAL_SECTION),
			cfg.bindEnum(ScrollBarMode.class, scrollPane::setScrollBar, scrollPane::getScrollBar, Constants.SCROLL_BAR_KEY, Constants.TERMINAL_SECTION),
//			cfg.bindBoolean(scroller.getNativeComponent()::setVisible, scroller.getNativeComponent()::isVisible, Constants.SCROLL_BAR_KEY, Constants.TERMINAL_SECTION),
			cfg.bindString((t) -> {
				theme = ttyContext.getContainer().getThemes().resolve(cfg.terminal().get(Constants.THEME_KEY));
				applyTheme();
			}, null, Constants.DARK_MODE_KEY, Constants.UI_SECTION)
		));
		
		try {
			startShell(bldr.request.map(rq -> rq.shell().orElseGet(this::shellForTty)).orElseGet(this::shellForTty));
		}
		catch(Exception e) {
			/* Not a known shell, so just treat as a custom command */
			/* Used to prompt for some connection parameters */
			LOG.error("Failed to start protocol.", e);
			runProtocol(new ErrorProtocol(RESOURCES.getString("failedToStartShell"), e));
		}
	}
	
	public void attached(TerminalProtocol consoleProtocol) {
		runLater(this::updateState);
		
	}
	
	public Optional<String> canClose() {
		var proto = protocol();
		return proto == null ? Optional.empty() : proto.canClose();
	}

	public PricliPopup cli() {
		if(pricli == null) {
			pricli = new PricliPopup(this);
			pricli.shell().cwd(cwd);
		}
		return pricli;
	}

	public int clipboardToHost(Clipboard clipboard) {
		if(Platform.isFxApplicationThread()) {
			var text = String.valueOf(clipboard.getContent(DataFormat.PLAIN_TEXT));
			terminalPanel.getViewport().enqueue(() -> terminalPanel.getViewport().output(text));
			showOverlayTextInfo(overlayInfo, RESOURCES.getString("pasted"));
			return text.length();
		}
		else {
			var res = new AtomicInteger();
			var sem = new Semaphore(1);
			try {
				sem.acquire();
				Platform.runLater(() -> {
					res.set(clipboardToHost(clipboard));
					sem.release();
				});
				sem.acquire();
				return res.get();
			}
			catch(InterruptedException ie) {
				return 0;
			}
		}
	}
	
	@Override
	public void close() {
		if(!closed) {
			closed = true;
			try {
				synchronized(protocols) {
					while(!protocols.isEmpty()) {
						var p = protocols.peek();
						try {
							p.close();
						}
						finally {
							if(!protocols.isEmpty())
								protocols.pop();
						}
					}
				}
				handles.forEach(Handle::close);
			}
			finally {
				try {
					terminalPanel.close();
				}
				finally {
					try {
						onClose.ifPresent(oc -> oc.accept(this));
					}
					finally {
						if(statusEmulator != null) {
							try {
								statusEmulator.close();
							} catch (IOException e) {
							}
						}
					}
				}
			}
		}
	}
	
	public void cwd(Path cwd) {
		if(!Objects.equals(this.cwd, cwd)) {
			this.cwd = cwd;
			if(pricli != null) {
				pricli.shell().cwd(cwd);
			}
		}
	}
	
	public void detached(TerminalProtocol consoleProtocol) {
		runLater(this::updateState);		
	}
	
	public Map<String, String> environment() {
		var env = new HashMap<String, String>();
		env.put("TERM", terminalPanel.getViewport().getTerminalType().getId());

		var theme = ttyContext.getContainer().getSelectedTheme();
		addEnvVarIfPresentInTheme(theme, TerminalTheme.LS_COLORS, env);
		addEnvVarIfPresentInTheme(theme, TerminalTheme.LSCOLORS, env);
		
		env.putAll(terminalPanel.getViewport().getTerminalType().getEnvironment());
		
		ttyContext.getContainer().getConfiguration().document().sectionOr(Constants.ENVIRONMENT_SECTION).ifPresent(sec -> {
			for(var en : sec.values().entrySet()) { 
				env.put(en.getKey(), en.getValue()[0].length() == 0 ? "" : en.getValue()[0]); 
			}	
		});
		
		return env;
	}
	
	public void hidePricli() {
		if(pricli != null && pricli.showing()) {
			pricli.hide();
		}
	}
	
	public TerminalProtocol protocol() {
		return protocols.isEmpty() ? null : protocols.peek();
	}

	public void protocol(TerminalProtocol protocol) {
		if(protocol == null || this.protocols.contains(protocol))
			throw new IllegalArgumentException();
		
		var last = this.protocols.isEmpty() ? null : this.protocols.peek();
		if(last != null) {
			try {
				last.detach();
			}
			catch(Exception ioe) {
				LOG.warn("Failed to detect previous protocol. You may experience strange behaviour.", ioe);
			}
		}
		
		/* Take a copy of the line the cursor is currently at. We reprint this 
		 * when we switch back to whatever protocol was active before this one.
		 */
		var vp = terminalPanel.getViewport();
		@SuppressWarnings("unused")
		var bufferRow = vp.getPage().data().get(vp.displayRowToBufferRow(vp.getPage().cursorY())).clone(0, vp.getPage().data());
		@SuppressWarnings("unused")
		var cursorX = vp.getPage().cursorX();
		
		this.protocols.push(protocol);
		runLater(this::updateState);
		while(protocol != null) {
			try {
				protocol.attach(this);
			}
			catch(RuntimeException re) {
				LOG.error("Protocol failed.", re);
				throw re;
			}
			catch(Throwable re) {
				LOG.error("Protocol failed.", re);
				throw new IllegalStateException(re);
			}
			finally {
				try {
					synchronized(protocols) {
						if(!this.protocols.isEmpty()) {
							this.protocols.pop();
						}
						if(this.protocols.isEmpty()) {
							if(!closed) {
								close();
							}
							return;
						}
					}
					try {
						if(protocol.isAttached()) 
							protocol.detach();
					}
					finally { 
//							try {
//								if(vp.getCursorX() > 0) {
//									vp.cr();
//									vp.newline();
//								}
//								vp.writeString(Styling.styled(MessageFormat.format(RESOURCES.getString("disconnected"), protocol.displayName())).toAnsi());
//								vp.newline();
//								vp.cr();
//								vp.getBufferData().get(vp.displayRowToBufferRow(vp.getCursorY())).copyFrom(bufferRow);
//								vp.setCursorX(cursorX);
//								vp.flush();
//							} catch (IOException e) {
//								throw new UncheckedIOException(e);
//							} finally {
							protocol = protocol();
//							}
					}
				}
				finally {
					runLater(this::updateState);
				}
			}
		}
		
	}

	public Stack<TerminalProtocol> protocols() {
		return protocols;
	}

	public void setClipboard(ClipboardContent content) {
		FXUtil.maybeQueue(() -> {
			Clipboard.getSystemClipboard().setContent(content);
			showOverlayTextInfo(overlayInfo, RESOURCES.getString("copied")); 
		});
	}
	
	public StringProperty shortTitle() {
		return shortTitle;
	}
	
	public void showPricli() {
		if(pricli == null || !pricli.showing()) {
			cli().show();
		}
	}
	
	public Status status() {
		return status;
	}

	public ObjectProperty<Color> tabColor() {
		return tabColor;
	}

	public JavaFXTerminalPanel terminal() {
		return terminalPanel;
	}

	public StringProperty title() {
		return title;
	}

	public void togglePricli() {
		if(pricli == null || !pricli.showing()) {
			showPricli();
		}
		else 
			hidePricli();
	}

	public TTYContext ttyContext() {
		return ttyContext;
	}

	public StringProperty userTitle() {
		return userTitle;
	}

	private void addEnvVarIfPresentInTheme(TerminalTheme theme, String key, Map<String, String> map) {
		theme.ini().sectionOr("environment").ifPresent(sec -> sec.getOr(key).ifPresent(val -> {
			map.put(key, val);
		}));
	}
	
	private void applyColors() {
		var ss = getStylesheets();
		writeJavaFXCSS();
		var tmpFile = getCustomJavaFXCSSFile(); /*TODO per tab / window ? */
		var uri = tmpFile.toUri().toString();
		ss.remove(uri);
		ss.add(0, uri);
	}
	
	private void applyTheme() {
		theme.apply(terminalPanel, getBackgroundOpacity());
		if(statusTerminal != null)
			theme.apply(statusTerminal, 100);
		applyColors();
	}

	private void checkStatusDisplay() {
		var statusCfg = ttyContext.getContainer().getConfiguration().status();
		var closed = statusTerminal.getViewport().getPage().data().isClosed();
		var enable = !closed && statusCfg.getBoolean(Constants.ENABLED_KEY);
		var type = enable ? ((DECModes)terminalPanel.getViewport().getModes()).getStatusLineType() : StatusLineType.NONE;
		var isType = getStatusLineType();
		if(enable != statusTerminal.getControl().isVisible()) {
			maybeQueue(() -> statusTerminal.getControl().setVisible(enable));
		}
		if(closed) {
			return;
		}
		
		if(LOG.isDebugEnabled()) {
			LOG.debug("Check status display. Type {}, Enable {}, Should Show {}", type, enable, isType);
		}
		
		if(type != isType) {
			if(type == StatusLineType.INDICATOR) {
				if(!status.isAttached()) {
					status.attach(statusTerminal.getViewport());
				}
				updateIndicatorStatus();
			}
			else {
				if(status.isAttached()) {
					status.detach();
				}
				if(isType == StatusLineType.INDICATOR || type == StatusLineType.NONE) {
					statusTerminal.getViewport().clearScreen();	
				}
			}
		}
		else {
			if(type == StatusLineType.INDICATOR) {
				updateIndicatorStatus();
			}
		}
	}
	
	private int getBackgroundOpacity() {
		return (int)((float)terminalPanel.getViewport().getColors().getBG().getAlphaRatio() * 100f);
	}
	
	private int[] getConfiguredSize() {
		var prefs = ttyContext.getContainer().getConfiguration();
		var ssz = prefs.terminal().get(Constants.SCREEN_SIZE_KEY);
		try {
			var arr = ssz.split("x");
			int sw = Integer.parseInt(arr[0]);
			int sh = Integer.parseInt(arr[1]);
			return new int[] { sw, sh };
		} catch (Exception e) {
			LOG.warn("Failed to parse configured screen size, defaulting to 80x24.");
			return new int[] { 80, 24 };
		}
	}
	
	private Path getCustomJavaFXCSSFile() {
		// TODO per window file
		return Paths.get(System.getProperty("java.io.tmpdir")).resolve(System.getProperty("user.name") + "-pretty")
				.resolve("term.css");
	}
	
	private String getCustomJavaFXCSSResource() {
		var bui = new StringBuilder();
		var colors = terminalPanel.getViewport().getColors();
		var bgCol = colors.getBG();
		var bgStr = ttyContext.getContainer().isDecorated() ? bgCol.toCSSRGB() :  bgCol.toCSSRGBA();
		var bgOpaqueStr = ttyContext.getContainer().isDecorated() ? bgCol.toCSSRGB() :  bgCol.toCSSRGBA();
		var fgCol = colors.getFG();
		var fgStr = fgCol.toHTMLColor();
		var uiToolkit = terminalPanel.getUIToolkit();
		var aStr = Colors.toHex(
				Colors.accent(uiToolkit.localColorToNativeColor(bgCol), uiToolkit.localColorToNativeColor(fgCol)));
		bui.append("* {\n");
		bui.append("-fx-terminal-fg: ");
		bui.append(fgStr);
		bui.append(";\n");
		bui.append("-fx-terminal-bg: ");
		bui.append(bgStr);
		bui.append(";\n");
		bui.append("-fx-terminal-bg-opaque: ");
		bui.append(bgOpaqueStr);
		bui.append(";\n");
		bui.append("-fx-terminal-accent: ");
		bui.append(aStr);
		bui.append(";\n");
		bui.append("}\n");

		return bui.toString();

	}

	private String[] getDisabledFeatures() {
		return terminalPanel.getViewport().enabled().stream().map(Feature::toString).toList().toArray(new String[0]);
	}
	
	private String[] getEnabledFeatures() {
		return terminalPanel.getViewport().enabled().stream().map(Feature::toString).toList().toArray(new String[0]);
	}
	
	private Stage getStage() {
		var w = getWindow();
		return w instanceof Stage ? (Stage)w  : null;
	}
	
	private String getThemeId() {
		return theme.id();
	}

	private Window getWindow() {
		var scn = getScene();
		return scn == null ? null : (Stage) scn.getWindow();
	}

	private StatusLineType getStatusLineType() {
		if(scrollPane.getBottom() == null || !scrollPane.getBottom().isVisible()) {
			return StatusLineType.NONE;
		}
		else if(status.isAttached()) {
			return StatusLineType.INDICATOR;
		}
		else {
			return StatusLineType.HOST_WRITABLE;
		}
	}

	private boolean isStatusDisplay() {
		return getStatusLineType() != StatusLineType.NONE;
	}

	private void runProtocol(TerminalProtocol proto) {
		new Thread(() -> {
			try {
				protocol(proto);
				LOG.info("Main loop exited normally.");	
			}
			catch(Throwable e) {
				LOG.error("Main loop error.", e);
			}
			finally {
				LOG.info("Main loop exited.");				
			}
		}, "TTY-" + counter.getAndIncrement()).start();
	}

	private void setBackgroundOpacity(int opacity) {
		theme.apply(terminalPanel, opacity);
		applyColors();
	}

	private void setFonts(String[] fonts) {
		ttyContext.getContainer().getFonts().setFonts(fonts);
		updateFont();
	}
	
	private void setFontSize(int sz) {
		updateFont();
	}
	
	private void setResizeStrategy(ResizeStrategy resizeStrategy) {
		terminalPanel.setResizeStrategy(resizeStrategy);
		updateAppearance();
	}
	
	private void setThemeId(String themeId) {
		theme = ttyContext.getContainer().getThemes().resolve(themeId);
		applyTheme();
	}

	private Shell shellForTty() {
		var id = ttyContext.getContainer().getConfiguration().terminal().get(Constants.SHELL_KEY);
		if(id.equals("")) {
			return ttyContext.getContainer().getShells().getById(Shells.NATIVE).get();
		}
		else {
			return ttyContext.getContainer().getShells().getById(id).orElseGet(() -> 
				Shell.forCommand(id)
			);
		}
	}

	@SuppressWarnings("unused")
	private void showOverlayTextInfo(Label node, String text) {
		if(resizeFade != null) {
			resizeFade.stop();
		}
		
		node.setText(text);
		
		var fadeIn = new FadeTransition(Duration.millis(125));
		if(node.isVisible()) {
			fadeIn.setFromValue(node.getOpacity());
		}
		else {
			fadeIn.setFromValue(0);
			node.setVisible(true);
		}
		fadeIn.setToValue(0.9f);
		fadeIn.setInterpolator(Interpolator.EASE_BOTH);
		
		var fadeOut = new FadeTransition(Duration.millis(500));
		fadeOut.setFromValue(0.9f);
		fadeOut.setToValue(0);
		fadeOut.setInterpolator(Interpolator.EASE_BOTH);
		fadeOut.setOnFinished(evt -> node.setVisible(false));
		
		resizeFade = new SequentialTransition(node, fadeIn, new PauseTransition(Duration.seconds(1)), fadeOut);
		
		resizeFade.play();
		
		
	}

	private void startShell(final Shell shell) {
		var bldr = new ConsoleProtocol.Builder();
		var cfg = ttyContext.getContainer().getConfiguration();
		var tsec = cfg.terminal();
		var loginShell = tsec.getBoolean(Constants.LOGIN_SHELL_KEY);
		var console = tsec.getBoolean(Constants.CONSOLE_KEY);
		var legacyPty = tsec.getBoolean(Constants.LEGACY_PTY_KEY);
		var windowsAnsiColor = tsec.getBoolean(Constants.WINDOWS_ANSI_COLOR_KEY);
		var preservePty = tsec.getBoolean(Constants.PRESERVE_PTY_KEY);
		
		Shell fShell;
		if(shell.commandName().equals(Shells.NATIVE)) {
			fShell = ttyContext.getContainer().getShells().getDefault().orElseThrow(() -> new IllegalStateException("No default shell available."));
		}
		else {
			fShell = shell;
		}
		if(fShell.type() == ShellType.BUILTIN) {
			runProtocol(new PricliProtocol(shell.toFullCommandText(loginShell)));
		}
		else {
			if(fShell.cygwin()) {
				LOG.warn("Cygwin requested, but this is not yet working. Ignoring.");
			}
			
			runProtocol(bldr.
				withPath(ttyContext.getContainer().getDefaultWorkingDirectory()).
				withCommandLine(fShell.fullCommand(loginShell)).
				//withCygwin(fShell.cygwin()).
				withConsole(console).
				withDefaultName(() ->
					fShell.name() + " " + nextVirtualConsoleNumber(fShell.id())
				).
				withUseWinConPty(!legacyPty).
				withWindowsAnsiColorEnabled(windowsAnsiColor).
				withUnixOpenTtyToPreserveOutputAfterTermination(preservePty).
				build());
		}
	}

	private void updateAppearance() {
		var rs = terminalPanel.getResizeStrategy();
		if (rs == ResizeStrategy.FONT) {
			var ssz = getConfiguredSize();
			var buf = terminalPanel.getViewport();
			LOG.info("Sizing font for {} x {}", ssz[0], ssz[1]);
			buf.enqueue(() -> {
				buf.setScreenSize(ssz[0], ssz[1], false);
			});
		} else if (getStage() != null && rs == ResizeStrategy.SCREEN) {
//			LOG.info("Sizing to scene");
//			getStage().sizeToScene();
//			LOG.info("Sized to scene");
		}
	}
	
	private void updateFeatures() {
		var vp = terminalPanel.getViewport();
		var cfg = ttyContext.getContainer().getConfiguration();
		
		vp.resetFeatures();
		vp.enable(Arrays.asList(
				cfg.terminal().getAll(Constants.ENABLED_FEATURES)).
				stream().map(fn->Feature.Registry.get().get(fn)).filter(Optional::isPresent).map(Optional::get).toList().toArray(new Feature[0]));
		vp.disable(Arrays.asList(
				cfg.terminal().getAll(Constants.DISABLED_FEATURES)).
				stream().map(fn->Feature.Registry.get().get(fn)).filter(Optional::isPresent).map(Optional::get).toList().toArray(new Feature[0]));
	}
	
	private void updateFont() {
		var fnt = terminalPanel.getFontManager().getFonts(false).stream().findFirst().orElseThrow(()-> new IllegalStateException("No primary fonts selected."));
		try {
			terminalPanel.setTerminalFont(new FontSpec(fnt.spec().getName(), ttyContext.getContainer().getConfiguration().terminal().getInt(Constants.FONT_SIZE_KEY)));
		} catch (Exception e) {
			LOG.warn("Failed to set font.", e);
		}
		updateAppearance();
	}

	private void updateIndicatorStatus() {
		status.redraw(false);
	}
	
	private void updateStatusSize() {
		statusTerminal.getViewport().setScreenSize(terminalPanel.getViewport().getColumns(), ttyContext.getContainer().getConfiguration().status().getInt(Constants.HEIGHT_KEY), false);
		if(getStatusLineType() == StatusLineType.INDICATOR) {
			status.redraw(true);
		}
	}
	
	private void updateState() {
		var proto = protocol();
		if(userTitle.getValue().equals("")) {
			shortTitle.setValue(proto == null ? RESOURCES.getString("appType") : proto.displayName());
		}
		else {
			shortTitle.setValue(userTitle.getValue());
		}
		terminalPanel.getViewport().enqueue(() -> checkStatusDisplay());
	}

	private void writeJavaFXCSS() {
		try {
			var tmpFile = getCustomJavaFXCSSFile();
			Files.createDirectories(tmpFile.getParent());
			try (var pw = new PrintWriter(Files.newBufferedWriter(tmpFile))) {
				pw.println(getCustomJavaFXCSSResource());
			}
		} catch (IOException e) {
			throw new RuntimeException("Could not create custom CSS resource.", e);
		}
	}


}
