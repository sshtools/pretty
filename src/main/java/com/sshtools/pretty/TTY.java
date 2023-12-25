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
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Stack;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.Data.Handle;
import com.sshtools.pretty.Configuration.TriState;
import com.sshtools.pretty.pricli.Pricli;
import com.sshtools.terminal.emulation.CursorStyle;
import com.sshtools.terminal.emulation.Feature;
import com.sshtools.terminal.emulation.ResizeStrategy;
import com.sshtools.terminal.emulation.emulator.DECEmulator;
import com.sshtools.terminal.emulation.emulator.DECModes;
import com.sshtools.terminal.emulation.emulator.DECModes.MouseReport;
import com.sshtools.terminal.emulation.emulator.DECModes.StatusLineType;
import com.sshtools.terminal.emulation.fonts.FontSpec;
import com.sshtools.terminal.vt.javafx.JavaFXScrollBar;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.Resource;

@Bundle
@Resource
@Reflectable(all = true)
public class TTY extends StackPane implements Closeable {

	static Logger LOG = LoggerFactory.getLogger(TTY.class);

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(TTY.class.getName());

	private static final String STATUS_SECTION = "status";

	private final JavaFXTerminalPanel terminalPanel;
	private final TTYContext app;
	private final List<Handle> handles = new LinkedList<>();

	private Pricli pricli;
	private TerminalTheme theme = TerminalTheme.getDefault();
	private BorderPane scrollPane;
	private JavaFXTerminalPanel statusTerminal;
	private final Stack<TerminalProtocol> protocols = new Stack<>();
	private final StringProperty title = new SimpleStringProperty(RESOURCES.getString("title"));
	private final StringProperty shortTitle = new SimpleStringProperty(RESOURCES.getString("appType"));
	private final Consumer<TTY> onClose;
	private final Status status = new Status();

	private boolean closed;

	private JavaFXScrollBar scroller;

	public TTY(TTYContext app, Consumer<TTY> onClose) {
		this.app = app;
		this.onClose = onClose;
		
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
		var emulator = new DECEmulator<JavaFXTerminalPanel>("xterm-256color", sz[0], sz[1]);
		var buf = emulator.getPage().data();

		/* Create and configure terminal */
		terminalPanel = new JavaFXTerminalPanel.Builder().
				withUiToolkit(app.getContainer().getUiToolkit()).
				withFontManager(app.getContainer().getFontManager()).
				withBuffer(emulator)
				.build();
		theme.apply(terminalPanel);
		
		
		/* Configure terminal's buffer */
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
			cfg.bindEnum(ResizeStrategy.class, this::setResizeStrategy, terminalPanel::getResizeStrategy, "resize-strategy", Options.TERMINAL_SECTION),
			cfg.bindInteger(emulator::setMaximumBufferSize, buf::getMaximumSize, "buffer-size", Options.TERMINAL_SECTION),
			cfg.bindString(emulator::setTerminalType, emulator.getTerminalType()::getId, "type", Options.TERMINAL_SECTION),  
			cfg.bindString((nsz) -> updateAppearance(), () -> String.format("%dx%d", emulator.getColumns(), emulator.getRows()), "screen-size", Options.TERMINAL_SECTION),
			cfg.bindInteger(this::setFontSize, terminalPanel.getFontManager().getDefault().spec()::getSize, "font-size", Options.TERMINAL_SECTION),
			cfg.bindStrings(this::setFonts, this::getFonts, "fonts", Options.TERMINAL_SECTION),
			cfg.bindStrings((s) -> updateFeatures(), this::getEnabledFeatures, "enabled-features", Options.TERMINAL_SECTION), 
			cfg.bindStrings((s) -> updateFeatures(), this::getDisabledFeatures, "disabled-features", Options.TERMINAL_SECTION),
			cfg.bindString(this::setThemeName, this::getThemeName, "theme", Options.TERMINAL_SECTION),
			cfg.bindBoolean((s) -> checkStatusDisplay(), this::isStatusDisplay, "enabled", STATUS_SECTION),
			cfg.bindBoolean(emulator::setEnableScrollback, emulator::isEnableScrollback, "scroll-back", Options.TERMINAL_SECTION),
			cfg.bindBoolean(emulator::setEnableBlinking, emulator::isEnableBlinking, "blinking", Options.TERMINAL_SECTION),
			cfg.bindBoolean(emulator.getModes()::setCursorBlink, emulator.getModes()::isCursorBlink, "cursor-blink", Options.TERMINAL_SECTION),
			cfg.bindEnum(CursorStyle.class, terminalPanel::setCursorStyle, terminalPanel::getCursorStyle, "cursor-style", Options.TERMINAL_SECTION),
			cfg.bindEnum(TriState.class, this::setScrollBar, this::getScrollBar, "scroll-bar", Options.TERMINAL_SECTION),
			cfg.bindBoolean(scroller.getNativeComponent()::setVisible, scroller.getNativeComponent()::isVisible, "scroll-bar", Options.TERMINAL_SECTION)
		));

		getChildren().add(scrollPane);
		terminalPanel.setOnBeforeKeyTyped(ke -> {
			if(ke.isControlDown() && ke.isShiftDown() && ke.getCharacter().equals("C")) {
				var content = new ClipboardContent();
				synchronized(terminalPanel.getViewport().getBufferLock()) {
					content.putString(terminalPanel.getViewport().getSelection());
				}
				Clipboard.getSystemClipboard().setContent(content);
				ke.consume();
			}
			else if(ke.isControlDown() && ke.isShiftDown() && ke.getCharacter().equals("V")) {
				var text = String.valueOf(Clipboard.getSystemClipboard().getContent(DataFormat.PLAIN_TEXT));
				synchronized(terminalPanel.getViewport().getBufferLock()) {
					terminalPanel.getViewport()
						.output(text);
				}
				ke.consume();
			}
			else if(ke.isControlDown() && ke.isShiftDown() && ke.getCharacter().equals("T")) {
				app.newTab();
				ke.consume();
			}
			else if(ke.isControlDown() && ke.isShiftDown() && ke.getCharacter().equals("N")) {
				app.newWindow();
				ke.consume();
			}
			else if(ke.isAltDown() && ke.getCharacter().equals("/")) {
				togglePricli();
				ke.consume();
			}
		});
		
		/* Status */
		status.add(new Status.SizeAndCursor(this));
		status.add(new Status.InsertReplaceMode(this));

		/* Setup */
		updateAppearance();
		updateState();
		
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
			e.printStackTrace();
		});

		/* Used to prompt for some connection parameters */
		new Thread(() -> {
			try {
				protocol(new ConsoleProtocol.Builder().build());
				LOG.info("Main loop exited normally.");	
			}
			catch(Throwable e) {
				LOG.error("Main loop error.", e);
			}
			finally {
				LOG.info("Main loop exited.");				
			}
		}, "InitialProtocol").start();
	}
	
	public Status status() {
		return status;
	}
	
	public StringProperty shortTitle() {
		return shortTitle;
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
			catch(Exception re) {
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

	private void togglePricli() {
		if(pricli == null)
			pricli = new Pricli(this);
		else 
			pricli.show();
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
				onClose.accept(this);
			}
		}
	}

	public TTYContext getTTYContext() {
		return app;
	}

	public JavaFXTerminalPanel terminal() {
		return terminalPanel;
	}

	public TerminalProtocol protocol() {
		return protocols.isEmpty() ? null : protocols.peek();
	}

	public void attached(ConsoleProtocol consoleProtocol) {
		runLater(this::updateState);
		
	}

	public void detached(ConsoleProtocol consoleProtocol) {
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
				cfg.getAll("enabled-features", Options.TERMINAL_SECTION)).
				stream().map(fn->Feature.Registry.get().get(fn)).filter(Optional::isPresent).map(Optional::get).toList().toArray(new Feature[0]));
		vp.disable(Arrays.asList(
				cfg.getAll("disabled-features", Options.TERMINAL_SECTION)).
				stream().map(fn->Feature.Registry.get().get(fn)).filter(Optional::isPresent).map(Optional::get).toList().toArray(new Feature[0]));
	}
	
	private String[] getDisabledFeatures() {
		return terminalPanel.getViewport().enabled().stream().map(Feature::toString).toList().toArray(new String[0]);
	}
	
	private String[] getFonts() {
		var fnts = terminalPanel.getFontManager().getFonts(true);
		var nl = fnts.stream().map(mf -> mf.spec().getName()).toList();
		return nl.toArray(new String[0]);
	}
	
	private void setFonts(String[] fonts) {
		var fmgr = terminalPanel.getFontManager();
		var i = 0;
		if(fonts == null || fonts.length == 0 || (/* TODO don't like this */ fonts.length == 1 && fonts[0].equals(""))) {
			fonts = getFonts();
		}
		for(var name : fonts) {
			var fnt = fmgr.getFont(name);
			if(fnt == null)
				LOG.warn("No such font as {}", fonts[i]);
			else
				fmgr.move(fnt, i++);
		}
		updateFont();
	}
	
	private void setFontSize(int sz) {
		updateFont();
	}

	private void updateFont() {
		var fnt = terminalPanel.getFontManager().getFonts(false).stream().findFirst().orElseThrow(()-> new IllegalStateException("No primary fonts selected."));
		try {
			terminalPanel.setTerminalFont(new FontSpec(fnt.spec().getName(), app.getContainer().getConfiguration().getInt("font-size", Options.TERMINAL_SECTION)));
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
					var emulator = new DECEmulator<JavaFXTerminalPanel>("xterm-256color", sz[0], cfg.getInt("height", STATUS_SECTION));

					statusTerminal = new JavaFXTerminalPanel.Builder().
							withUiToolkit(app.getContainer().getUiToolkit()).
							withFontManager(app.getContainer().getFontManager()).
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
//						cfg.bindString(this::setThemeName, this::getThemeName, "theme", Options.TERMINAL_SECTION),
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
		var scene = getScene();
		if (scene == null)
			return;

		var node = scene.getRoot();
		var ss = node.getStylesheets();
		writeJavaFXCSS();
		var tmpFile = getCustomJavaFXCSSFile();
		var uri = tmpFile.toUri().toString();
		ss.remove(uri);
		ss.add(uri);
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
		
		return new TerminalMenu(terminalPanel, Arrays.asList(newTab, newWindow, new SeparatorMenuItem()), this::togglePricli, options, about).menu();
	}

	private Path getCustomJavaFXCSSFile() {
		// TODO per window file
		return Paths.get(System.getProperty("java.io.tmpdir")).resolve(System.getProperty("user.name") + "-notionator")
				.resolve("term.css");
	}

	private String getCustomJavaFXCSSResource() {
		var bui = new StringBuilder();
		var bgCol = terminalPanel.getDefaultBackground();
		var bgStr = bgCol.toHTMLColor();
		var fgCol = terminalPanel.getDefaultForeground();
		var fgStr = fgCol.toHTMLColor();
		var uiToolkit = terminalPanel.getUIToolkit();
		var aStr = Colors.toHex(
				Colors.accent(uiToolkit.localColorToNativeColor(bgCol), uiToolkit.localColorToNativeColor(fgCol)));
		bui.append("* {\n");
		bui.append("-fx-notionator-fg: ");
		bui.append(fgStr);
		bui.append(";\n");
		bui.append("-fx-notionator-bg: ");
		bui.append(bgStr);
		bui.append(";\n");
		bui.append("-fx-notionator-accent: ");
		bui.append(aStr);
		bui.append(";\n");
		bui.append("}\n");

		return bui.toString();

	}
	
	private String getThemeName() {
		return theme.getName();
	}
	
	private void setThemeName(String themeName) {
		theme = TerminalTheme.getTheme(themeName);
		theme.apply(terminalPanel);
		if(statusTerminal != null)
			theme.apply(statusTerminal);
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
		var ssz = prefs.get("screen-size", Options.TERMINAL_SECTION);
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
		shortTitle.setValue(proto == null ? RESOURCES.getString("appType") : proto.displayName());
		checkStatusDisplay();
	}


}
