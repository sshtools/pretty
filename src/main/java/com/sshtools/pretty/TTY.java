package com.sshtools.pretty;

import static javafx.application.Platform.runLater;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.Data.Handle;
import com.sshtools.pretty.Configuration.TriState;
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
import com.sshtools.terminal.emulation.emulator.DECEmulator;
import com.sshtools.terminal.emulation.emulator.DECModes;
import com.sshtools.terminal.emulation.emulator.DECModes.MouseReport;
import com.sshtools.terminal.emulation.emulator.DECModes.StatusLineType;
import com.sshtools.terminal.emulation.emulator.XTERM256Color;
import com.sshtools.terminal.emulation.fonts.FontSpec;
import com.sshtools.terminal.vt.javafx.JavaFXScrollBar;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Transition;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
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

	static Logger LOG = LoggerFactory.getLogger(TTY.class);

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(TTY.class.getName());

	private final static AtomicInteger counter = new AtomicInteger(1);

	private static final String STATUS_SECTION = "status";

	private final JavaFXTerminalPanel terminalPanel;
	private final TTYContext app;
	private final List<Handle> handles = new LinkedList<>();

	private PricliPopup pricli;
	private TerminalTheme theme;
	private BorderPane scrollPane;
	private JavaFXTerminalPanel statusTerminal;
	private final Stack<TerminalProtocol> protocols = new Stack<>();
	private final StringProperty title = new SimpleStringProperty(RESOURCES.getString("title"));
	private final StringProperty shortTitle = new SimpleStringProperty(RESOURCES.getString("appType"));
	private final StringProperty userTitle = new SimpleStringProperty("");
	private final Consumer<TTY> onClose;
	private final Status status = new Status();
	private boolean closed;
	private JavaFXScrollBar scroller;
	private Transition resizeFade;
	private Path cwd;

	private Label overlayInfo;

	@SuppressWarnings("resource")
	public TTY(TTYContext app, Consumer<TTY> onClose) {
		this.app = app;
		this.onClose = onClose;
		this.cwd = Paths.get(System.getProperty("user.dir"));
		theme = app.getContainer().getSelectedTheme();
		
		var cfg = app.getContainer().getConfiguration();

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
		var bufferData = emulator.getPage().data();

		/* Create and configure terminal */
		terminalPanel = new JavaFXTerminalPanel.Builder().
				withAudioSystem(new TTYAudioSystem(this)).
				withUiToolkit(app.getContainer().getUiToolkit()).
				withFontManager(app.getContainer().getFonts().getFontManager()).
				withBuffer(emulator)
				.build();
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
				runLater(() -> checkStatusDisplay());
		});

		var term = terminalPanel.getControl();
		var contextMenu = createContextMenu();

		/* Scrolling */
		scroller = new JavaFXScrollBar(emulator.getDisplay());
		scroller.setAutoHide(true);

		scrollPane = new BorderPane();
		scrollPane.setOnMousePressed((evt) -> {
			var mouseMode = emulator.getModes().getMouseReport() != MouseReport.OFF; 
			if ((!mouseMode || (mouseMode && evt.isAltDown())) && evt.isPopupTrigger()) {
				contextMenu.show(term, evt.getScreenX(), evt.getScreenY());
				evt.consume();
			} else {
				contextMenu.hide();
			}
			
		});
		scrollPane.setOnMouseReleased((evt) -> {
			var mouseMode = emulator.getModes().getMouseReport() != MouseReport.OFF; 
			if (!mouseMode && evt.isPopupTrigger()) {
				contextMenu.show(term, evt.getScreenX(), evt.getScreenY());
				evt.consume();
			} 
		});
		scrollPane.setRight(scroller.getNativeComponent());
		scrollPane.setCenter(term);
		
		/* Bind to configuration */
		handles.addAll(Arrays.asList(
			cfg.bindEnum(ResizeStrategy.class, this::setResizeStrategy, terminalPanel::getResizeStrategy, "resize-strategy", Constants.TERMINAL_SECTION),
//			cfg.bindInteger(emulator::setMaximumBufferSize, bufferData::getMaximumSize, Constants.SCROLL_BACK_SIZE_KEY, Constants.TERMINAL_SECTION),
			cfg.bindInteger(this::setBackgroundOpacity, this::getBackgroundOpacity, "opacity", Constants.TERMINAL_SECTION),
			cfg.bindString(emulator::setTerminalType, emulator.getTerminalType()::getId, "type", Constants.TERMINAL_SECTION),  
			cfg.bindString(emulator::setTerminalType, emulator.getTerminalType()::getId, "type", Constants.TERMINAL_SECTION),  
			cfg.bindString((nsz) -> updateAppearance(), () -> String.format("%dx%d", emulator.getColumns(), emulator.getRows()), "screen-size", Constants.TERMINAL_SECTION),
			cfg.bindInteger(this::setFontSize, terminalPanel.getFontManager().getDefault().spec()::getSize, "font-size", Constants.TERMINAL_SECTION),
			cfg.bindStrings(this::setFonts, app.getContainer().getFonts()::getFonts, "fonts", Constants.TERMINAL_SECTION),
			cfg.bindStrings((s) -> updateFeatures(), this::getEnabledFeatures, "enabled-features", Constants.TERMINAL_SECTION), 
			cfg.bindStrings((s) -> updateFeatures(), this::getDisabledFeatures, "disabled-features", Constants.TERMINAL_SECTION),
			cfg.bindString(this::setThemeName, this::getThemeName, Constants.THEME_KEY, Constants.TERMINAL_SECTION),
			cfg.bindBoolean((s) -> checkStatusDisplay(), this::isStatusDisplay, "enabled", STATUS_SECTION),
			cfg.bindBoolean(emulator::setEnableScrollback, emulator::isEnableScrollback, Constants.SCROLL_BACK_KEY, Constants.TERMINAL_SECTION),
			cfg.bindBoolean(emulator::setEnableBlinking, emulator::isEnableBlinking, "blinking", Constants.TERMINAL_SECTION),
			cfg.bindBoolean(emulator.getModes()::setCursorBlink, emulator.getModes()::isCursorBlink, "cursor-blink", Constants.TERMINAL_SECTION),
			cfg.bindEnum(CursorStyle.class, terminalPanel::setCursorStyle, terminalPanel::getCursorStyle, "cursor-style", Constants.TERMINAL_SECTION),
			cfg.bindEnum(TriState.class, this::setScrollBar, this::getScrollBar, "scroll-bar", Constants.TERMINAL_SECTION),
			cfg.bindBoolean(scroller.getNativeComponent()::setVisible, scroller.getNativeComponent()::isVisible, "scroll-bar", Constants.TERMINAL_SECTION)
		));
		
		cfg.getStringProperty(Constants.DARK_MODE_KEY, Constants.UI_SECTION).addListener((c,o,n) -> {
			theme = app.getContainer().getThemes().resolve(cfg.get(Constants.THEME_KEY, Constants.TERMINAL_SECTION));
			applyTheme();
		});

		/* Build this stack */
		getChildren().add(scrollPane);
		getChildren().add(overlayInfo);
		
		/* TODO This key handling is all a bit crap, at least on some OS's, annoyingly Linux
		 *      If a key press is consumed, the key typed event (if its valid) is still being fired.
		 *      This means for example Alt+/ will actually type that character AND open the sub-shell.
		 *      
		 *      Also, this should be configurable from an ini file
		 */
		
		terminalPanel.setOnBeforeKeyTyped(ke -> {
			if(ke.isAltDown() && ke.getCharacter().equals("/")) {
				ke.consume();
			}

			if(ke.isControlDown() && ke.isShiftDown() && ke.getCharacter().equals("T")) {
				ke.consume();
			}

			if(ke.isControlDown() && ke.isShiftDown() && ke.getCharacter().equals("N")) {
				ke.consume();
			}

			if(ke.isControlDown() && ke.isShiftDown() && ke.getCharacter().equals("C")) {
				ke.consume();
			}
			
			if(ke.isControlDown() && ke.isShiftDown() && ke.getCharacter().equals("V")) {
				ke.consume();
			}
			if(ke.isControlDown() && ke.isShiftDown() && ke.getCharacter().equals("A")) {
				ke.consume();
			}
		});

		terminalPanel.setOnBeforeKeyReleased(ke -> {
			
			if(ke.isAltDown() && ke.getCode() == KeyCode.SLASH) {
				showPricli();
				ke.consume();
			}

			if(ke.isControlDown() && ke.isShiftDown() && ke.getCode() == KeyCode.T) {
				app.newTab();
				ke.consume();
			}

			if(ke.isControlDown() && ke.isShiftDown() && ke.getCode() == KeyCode.N) {
				app.newWindow();
				ke.consume();
			}

			if(ke.isControlDown() && ke.isShiftDown() && ke.getCode() == KeyCode.A) {
				emulator.selectAll();
				ke.consume();
			}

			if(ke.isControlDown() && ke.isShiftDown() && ke.getCode() == KeyCode.C) {
				var content = new ClipboardContent();
				synchronized(terminalPanel.getViewport().getBufferLock()) {
					content.putString(terminalPanel.getViewport().getSelection());
				}
				setClipboard(content);
				ke.consume();
			}
			
			if(ke.isControlDown() && ke.isShiftDown() && ke.getCode() == KeyCode.V) {
				clipboardToHost(Clipboard.getSystemClipboard());
				ke.consume();
			}
		});
		
		userTitle.addListener((c,o,n) -> updateState());
		
		/* Status */
		status.add(new Status.SizeAndCursor(this));
		status.add(new Status.InsertReplaceMode(this));

		/* Setup */
		updateAppearance();
		updateState();
		

		try {
			startShell();
		}
		catch(Exception e) {
			LOG.error("Failed to start protocol.", e);
			runProtocol(new ErrorProtocol(RESOURCES.getString("failedToStartShell"), e));
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
	
	
	public void clipboardToHost(Clipboard clipboard) {
		var text = String.valueOf(clipboard.getContent(DataFormat.PLAIN_TEXT));
		synchronized(terminalPanel.getViewport().getBufferLock()) {
			terminalPanel.getViewport()
				.output(text);
		}
		runLater(() -> showOverlayTextInfo(overlayInfo, RESOURCES.getString("pasted")));
	}
	
	public void setClipboard(ClipboardContent content) {

		Clipboard.getSystemClipboard().setContent(content);
		runLater(() -> showOverlayTextInfo(overlayInfo, RESOURCES.getString("copied")));
	}

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
	
	public Status status() {
		return status;
	}
	
	public StringProperty shortTitle() {
		return shortTitle;
	}
	
	public StringProperty userTitle() {
		return userTitle;
	}
	
	public StringProperty title() {
		return title;
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
		var bufferRow = vp.getPage().data().get(vp.displayRowToBufferRow(vp.getPage().cursorY())).clone(0, vp.getPage().data());
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
					if(!this.protocols.isEmpty()) {
						this.protocols.pop();
					}
					if(this.protocols.isEmpty()) {
						if(!closed) {
							close();
						}
						break;
					}
					else {
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
				}
				finally {
					runLater(this::updateState);
				}
			}
		}
		
	}

	private boolean isStatusDisplay() {
		return scrollPane.getBottom() != null && scrollPane.getBottom().isVisible();
	}

	void togglePricli() {
		if(pricli == null || !pricli.showing()) {
			showPricli();
		}
		else 
			hidePricli();
	}

	public void hidePricli() {
		if(pricli != null && pricli.showing()) {
			pricli.hide();
		}
	}
	
	public PricliPopup cli() {
		if(pricli == null) {
			pricli = new PricliPopup(this);
			pricli.shell().cwd(cwd);
		}
		return pricli;
	}
	
	public void showPricli() {
		if(pricli == null || !pricli.showing()) {
			cli().show();
		}
	}
	
	public Map<String, String> environment() {
		var env = new HashMap<String, String>();
		env.put("TERM", terminalPanel.getViewport().getTerminalType().getId());

		var theme = app.getContainer().getSelectedTheme();
		addEnvVarIfPresentInTheme(theme, TerminalTheme.LS_COLORS, env);
		addEnvVarIfPresentInTheme(theme, TerminalTheme.LSCOLORS, env);
		
		env.putAll(terminalPanel.getViewport().getTerminalType().getEnvironment());
		
		app.getContainer().getConfiguration().sectionOr("environment").ifPresent(sec -> {
			for(var en : sec.values()) { 
				env.put(en.name(), en.value()[0].length() == 0 ? "" : en.value()[0]); 
			}	
		});
		
		return env;
	}

	public Optional<String> canClose() {
		var proto = protocol();
		return proto == null ? Optional.empty() : proto.canClose();
	}

	@Override
	public void close() {
		if(!closed) {
			closed = true;
			try {
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
				handles.forEach(Handle::close);
			}
			finally {
				try {
					terminalPanel.close();
				}
				finally {
					onClose.accept(this);
				}
			}
		}
	}

	public TTYContext ttyContext() {
		return app;
	}

	public JavaFXTerminalPanel terminal() {
		return terminalPanel;
	}

	public Stack<TerminalProtocol> protocols() {
		return protocols;
	}

	public TerminalProtocol protocol() {
		return protocols.isEmpty() ? null : protocols.peek();
	}

	public void attached(TerminalProtocol consoleProtocol) {
		runLater(this::updateState);
		
	}

	public void detached(TerminalProtocol consoleProtocol) {
		runLater(this::updateState);		
	}
	
	private void setScrollBar(TriState state) {
		switch(state) {
		case AUTO:
			scroller.setAutoHide(true);
			break;
		case ON:
			scroller.setAutoHide(false);
			scroller.getNativeComponent().setVisible(true);
			break;
		default:
			scroller.setAutoHide(false);
			scroller.getNativeComponent().setVisible(false);
			break;
		}
	}
	
	private TriState getScrollBar() {
		if(scroller.isAutoHide()) {
			return TriState.AUTO;
		}
		else if(scroller.getNativeComponent().isVisible()) {
			return TriState.ON;
		}
		else {
			return TriState.OFF;
		}
	}

	private String[] getEnabledFeatures() {
		return terminalPanel.getViewport().enabled().stream().map(Feature::toString).toList().toArray(new String[0]);
	}
	
	private void updateFeatures() {
		var vp = terminalPanel.getViewport();
		var cfg = app.getContainer().getConfiguration();
		
		vp.resetFeatures();
		vp.enable(Arrays.asList(
				cfg.getAll("enabled-features", Constants.TERMINAL_SECTION)).
				stream().map(fn->Feature.Registry.get().get(fn)).filter(Optional::isPresent).map(Optional::get).toList().toArray(new Feature[0]));
		vp.disable(Arrays.asList(
				cfg.getAll("disabled-features", Constants.TERMINAL_SECTION)).
				stream().map(fn->Feature.Registry.get().get(fn)).filter(Optional::isPresent).map(Optional::get).toList().toArray(new Feature[0]));
	}
	
	private String[] getDisabledFeatures() {
		return terminalPanel.getViewport().enabled().stream().map(Feature::toString).toList().toArray(new String[0]);
	}
	
	private void setFonts(String[] fonts) {
		app.getContainer().getFonts().setFonts(fonts);
		updateFont();
	}
	
	private void setFontSize(int sz) {
		updateFont();
	}

	private void updateFont() {
		var fnt = terminalPanel.getFontManager().getFonts(false).stream().findFirst().orElseThrow(()-> new IllegalStateException("No primary fonts selected."));
		try {
			terminalPanel.setTerminalFont(new FontSpec(fnt.spec().getName(), app.getContainer().getConfiguration().getInt("font-size", Constants.TERMINAL_SECTION)));
		} catch (Exception e) {
			LOG.warn("Failed to set font.", e);
		}
		updateAppearance();
	}
	
	private void checkStatusDisplay() {
		var enable = app.getContainer().getConfiguration().getBoolean("enabled", STATUS_SECTION);
		var type = ((DECModes)terminalPanel.getViewport().getModes()).getStatusLineType();
		var shouldShow = enable && type != StatusLineType.NONE;
		
		if(shouldShow != isStatusDisplay()) {
			if(shouldShow) {
				if(statusTerminal == null) {
				
					var sz = getConfiguredSize();
					var cfg = app.getContainer().getConfiguration();
					var emulator = new DECEmulator<JavaFXTerminalPanel>(terminalPanel.getViewport().getTerminalType(), sz[0], cfg.getInt("height", STATUS_SECTION));

					statusTerminal = new JavaFXTerminalPanel.Builder().
							withUiToolkit(app.getContainer().getUiToolkit()).
							withFontManager(app.getContainer().getFonts().getFontManager()).
							withBuffer(emulator)
							.build();
					statusTerminal.setResizeStrategy(ResizeStrategy.SCREEN);
					statusTerminal.setEventsEnabled(false);
					emulator.addResizeListener((term,cols,rows,remote) -> runLater(this::checkStatusDisplay));
					emulator.getModes().setShowCursor(false);
					
					var statusControl = statusTerminal.getControl();
					terminalPanel.getViewport().setStatusLineDisplay(statusTerminal);

					/* Bind to configuration */
					handles.addAll(Arrays.asList(
							/* TODO some need binding */
//						cfg.bindEnum(ResizeStrategy.class, this::setResizeStrategy, terminalPanel::getResizeStrategy, "resize-strategy", Options.TERMINAL_SECTION),
//						cfg.bindInteger(buf::setMaximumSize, buf::getMaximumSize, "buffer-size", Options.TERMINAL_SECTION),
//						cfg.bindString(emulator::setTerminalType, emulator.getTerminalType()::getId, "type", Options.TERMINAL_SECTION),  
//						cfg.bindString((nsz) -> updateAppearance(), () -> String.format("%dx%d", emulator.getColumns(), emulator.getRows()), "screen-size", Options.TERMINAL_SECTION),
//						cfg.bindInteger(this::setFontSize, terminalPanel.getFontManager().getDefault().spec()::getSize, "font-size", Options.TERMINAL_SECTION),
//						cfg.bindStrings(this::setFonts, this::getFonts, "fonts", Options.TERMINAL_SECTION),
//						cfg.bindStrings((s) -> updateFeatures(), this::getEnabledFeatures, "enabled-features", Options.TERMINAL_SECTION), 
//						cfg.bindStrings((s) -> updateFeatures(), this::getDisabledFeatures, "disabled-features", Options.TERMINAL_SECTION),
//						cfg.bindString(this::setThemeName, this::getThemeName, Constants.THEME_KEY, Options.TERMINAL_SECTION),
//						cfg.bindBoolean(this::setStatusDisplay, this::isStatusDisplay, "enabled", STATUS_SECTION),
//						cfg.bindBoolean(emulator::setEnableScrollback, emulator::isEnableScrollback, "scroll-back", Options.TERMINAL_SECTION),
//						cfg.bindBoolean(emulator::setEnableBlinking, emulator::isEnableBlinking, "blinking", Options.TERMINAL_SECTION),
//						cfg.bindBoolean(emulator.getModes()::setCursorBlink, emulator.getModes()::isCursorBlink, "cursor-blink", Options.TERMINAL_SECTION),
//						cfg.bindEnum(CursorStyle.class, terminalPanel::setCursorStyle, terminalPanel::getCursorStyle, "cursor-style", Options.TERMINAL_SECTION)
					));
					
					scrollPane.setBottom(statusControl);
				}
				
				if(type == StatusLineType.INDICATOR) {
					status.attach(statusTerminal.getViewport());
					updateIndicatorStatus();
				}
				else if(status.isAttached()) {
					status.detach();
				}
				
				statusTerminal.getControl().setVisible(true);
				statusTerminal.getControl().setManaged(true);
			}
			else if(statusTerminal != null) {
				statusTerminal.getControl().setVisible(false);
				statusTerminal.getControl().setManaged(enable);
			}
		}
		else {
			if(type == StatusLineType.INDICATOR) {
				updateIndicatorStatus();
			}
		}
	}
	
	private void updateIndicatorStatus() {
		status.redraw();
	}
	
	private void setResizeStrategy(ResizeStrategy resizeStrategy) {
		terminalPanel.setResizeStrategy(resizeStrategy);
		updateAppearance();
	}
	
	private void about() {
		app.getContainer().about(app.stage());
	}

	private Stage getStage() {
		var w = getWindow();
		return w instanceof Stage ? (Stage)w  : null;
	}

	private Window getWindow() {
		var scn = getScene();
		return scn == null ? null : (Stage) scn.getWindow();
	}

	private void applyColors() {
		var ss = getStylesheets();
		writeJavaFXCSS();
		var tmpFile = getCustomJavaFXCSSFile(); /*TODO per tab / window ? */
		var uri = tmpFile.toUri().toString();
		ss.remove(uri);
		ss.add(0, uri);
	}

	private ContextMenu createContextMenu() {

		var newTab = new MenuItem(RESOURCES.getString("newTab"));
		newTab.setOnAction((e) -> app.newTab());
		newTab.setAccelerator(KeyCombination.keyCombination("CTRL+SHIFT+T"));

		var newWindow = new MenuItem(RESOURCES.getString("newWindow"));
		newWindow.setOnAction((e) -> app.newWindow());
		newWindow.setAccelerator(KeyCombination.keyCombination("CTRL+SHIFT+N"));
		
		var options = new MenuItem(RESOURCES.getString("options"));
		options.setOnAction((e) -> app.getContainer().options(app.stage()));

		var about = new MenuItem(RESOURCES.getString("about"));
		about.setOnAction((e) -> {
			e.consume();
			about(); 
		});
		
		return new TerminalMenu(terminalPanel, this::setClipboard, this::clipboardToHost, Arrays.asList(newTab, newWindow, new SeparatorMenuItem()), this::showPricli, options, about).menu();
	}

	private Path getCustomJavaFXCSSFile() {
		// TODO per window file
		return Paths.get(System.getProperty("java.io.tmpdir")).resolve(System.getProperty("user.name") + "-notionator")
				.resolve("term.css");
	}

	private String getCustomJavaFXCSSResource() {
		var bui = new StringBuilder();
		var colors = terminalPanel.getViewport().getColors();
		var bgCol = colors.getBG();
		var bgStr = app.getContainer().isDecorated() ? bgCol.toCSSRGB() :  bgCol.toCSSRGBA();
		var bgOpaqueStr = app.getContainer().isDecorated() ? bgCol.toCSSRGB() :  bgCol.toCSSRGBA();
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
	
	private String getThemeName() {
		return theme.name();
	}
	
	private int getBackgroundOpacity() {
		return (int)((float)terminalPanel.getViewport().getColors().getBG().getAlphaRatio() * 100f);
	}

	private void setBackgroundOpacity(int opacity) {
		theme.apply(terminalPanel, opacity);
		applyColors();
	}
	
	private void setThemeName(String themeName) {
		theme = app.getContainer().getThemes().resolve(themeName);
		applyTheme();
	}

	private void applyTheme() {
		theme.apply(terminalPanel, getBackgroundOpacity());
		if(statusTerminal != null)
			theme.apply(statusTerminal, 100);
		applyColors();
	}

	private void updateAppearance() {
		var rs = terminalPanel.getResizeStrategy();
		if (rs == ResizeStrategy.FONT) {
			var ssz = getConfiguredSize();
			var buf = terminalPanel.getViewport();
			LOG.info("Sizing font for {} x {}", ssz[0], ssz[1]);
			synchronized(buf.getBufferLock()) {
				buf.setScreenSize(ssz[0], ssz[1], false);
			}
		} else if (getStage() != null && rs == ResizeStrategy.SCREEN) {
			LOG.info("Sizing to scene");
			getStage().sizeToScene();
			LOG.info("Sized to scene");
		}
	}

	private int[] getConfiguredSize() {
		var prefs = app.getContainer().getConfiguration();
		var ssz = prefs.get("screen-size", Constants.TERMINAL_SECTION);
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

	private void addEnvVarIfPresentInTheme(TerminalTheme theme, String key, Map<String, String> map) {
		theme.ini().sectionOr("environment").ifPresent(sec -> sec.getOr(key).ifPresent(val -> {
			map.put(key, val);
		}));
	}
	
	private void updateState() {
		var proto = protocol();
		if(userTitle.getValue().equals("")) {
			shortTitle.setValue(proto == null ? RESOURCES.getString("appType") : proto.displayName());
		}
		else {
			shortTitle.setValue(userTitle.getValue());
		}
		checkStatusDisplay();
	}

	private void startShell() {
		var id = app.getContainer().getConfiguration().get(Constants.SHELL_KEY, Constants.TERMINAL_SECTION);
		Optional<Shell> shell;
		if(id.equals("")) {
			shell = app.getContainer().getShells().getById(Shells.NATIVE);
		}
		else {
			shell = app.getContainer().getShells().getById(id);
		}
		var bldr = new ConsoleProtocol.Builder();
		if(shell.isPresent()) {
			var shellObj = shell.get();
			if(shellObj.commandName().equals(Shells.NATIVE)) {
				shellObj = app.getContainer().getShells().getDefault().orElseThrow(() -> new IllegalStateException("No default shell available."));
			}
			if(shellObj.type() == ShellType.BUILTIN) {
				runProtocol(new PricliProtocol(shellObj.toFullCommandText()));
			}
			else {
				runProtocol(bldr.
					withPath(app.getContainer().getDefaultWorkingDirectory()).
					withCommandLine(shellObj.fullCommand()).
					withCygwin(shellObj.cygwin()).
					build());
			}
		}
		else {
			var parsedCommand = Strings.parseQuotedString(id);
			var cmdName = parsedCommand.get(0).trim();
			if(cmdName.equals("")) {
				cmdName = Shells.NATIVE;
			}
			runProtocol(bldr.
					withPath(app.getContainer().getDefaultWorkingDirectory()).
					withCommandLine(parsedCommand).
					build());
		}
		
	}

	private void runProtocol(TerminalProtocol proto) {
		/* Not a known shell, so just treat as a custom command */
		/* Used to prompt for some connection parameters */
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


}
