package com.sshtools.pretty.pricli;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.Data.Handle;
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

public final class PricliPopup {
	
	public enum PopupPosition {
		AUTO, TOP, BOTTOM
	}
	
	static final String PRICLI_SECTION = "pricli";
	static Logger LOG = LoggerFactory.getLogger(PricliPopup.class);
	private static final String SCREEN_TERM_TYPE = "xterm-256color";

	static {
		System.setProperty("picocli.ansi", "true");
	}

	private BooleanProperty visible = new SimpleBooleanProperty(false);
	private final Terminal jline;
	private final LineReader reader;
	private final JavaFXTerminalPanel term;
	private final Region root;
	private final TTY tty;
	private final TTYContext ttyContext;
	private Thread thread;
	private Pane overlay;
	private final List<Handle> handles = new LinkedList<>();
	private boolean autoAlign = true;
	private final PricliShell shell;

	public PricliPopup(TTY tty) {
		this.tty = tty;
		this.ttyContext = tty.ttyContext();

		var cfg = ttyContext.getContainer().getConfiguration();
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
		
		var contextMenu = new TerminalMenu(term, tty::setClipboard, tty::clipboardToHost, this::hide).menu();

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
//			cfg.bindInteger(vp::setMaximumBufferSize, bd::getMaximumSize, "buffer-size",PRICLI_SECTION),
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
		
		shell = new PricliShell(term, jline, tty, ttyContext) {
			@Override
			public void close() {
				PricliPopup.this.hide();
			}
		};

		reader = LineReaderBuilder.builder().
				completer(shell.systemRegistry().completer()).
				history(new DefaultHistory()).
				parser(shell.parser()).
				option(LineReader.Option.DISABLE_EVENT_EXPANSION, true).
				variable(LineReader.LIST_MAX, 50).
				variable(LineReader.HISTORY_FILE, ttyContext.getContainer().getConfiguration().dir().resolve("pretty.history")).
				terminal(jline).build();
		
		shell.attach(reader);

		show();
	}

	public void close() {
		handles.forEach(Handle::close);
		Platform.runLater(this::hide);
	}

	public void hide() {
		visible.set(false);
		tty.terminal().focusTerminal();
	}

	public Terminal jline() {
		return jline;
	}

	public PicocliCommandRegistry registry(String name, Object command) {
		return shell.registry(name, command);
	}

	public PricliShell shell() {
		return shell;
	}

	public void show() {
		startReader();
		visible.set(true);
	}

	public boolean showing() {
		return visible.get();
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

	private void startReader() {
		if (thread == null || !thread.isAlive()) {
			shell.systemRegistry().cleanUp();
			thread = new Thread(shell::readCommands, "PricliPrompt");
			thread.start();
		}
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

}
