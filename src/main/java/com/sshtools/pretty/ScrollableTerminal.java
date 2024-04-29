package com.sshtools.pretty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.pretty.Actions.Action;
import com.sshtools.pretty.Actions.On;
import com.sshtools.pretty.pricli.PricliShell;
import com.sshtools.terminal.emulation.emulator.DECEmulator;
import com.sshtools.terminal.emulation.emulator.DECModes.MouseReport;
import com.sshtools.terminal.emulation.events.ViewportEvent;
import com.sshtools.terminal.emulation.events.ViewportListener;
import com.sshtools.terminal.vt.javafx.JavaFXScrollBar;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Transition;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class ScrollableTerminal extends BorderPane {
	static Logger LOG = LoggerFactory.getLogger(ScrollableTerminal.class);

	private final StackPane termArea;
	private final JavaFXScrollBar scroller;
	private final Map<Object, Action> consumed = new HashMap<>();
	private DECEmulator<JavaFXTerminalPanel> emulator;
	private boolean inScrollBar;
	private Transition fading;

	@SuppressWarnings("unchecked")
	public ScrollableTerminal(TTYContext ttyContext, JavaFXTerminalPanel terminalPanel, ContextMenu contextMenu, PricliShell shell, On on) {
		
		emulator = (DECEmulator<JavaFXTerminalPanel>)terminalPanel.getViewport();
		emulator.addTerminalBufferListener(new ViewportListener() {
			@Override
			public void windowBaseChanged(int windowBase, ViewportEvent event) {
				if(getScrollBar() == ScrollBarMode.AUTOMATIC && !inScrollBar) {
					fadeInScrollBar(true);
				}
			}
		});
		
		var term = terminalPanel.getControl();

		getStyleClass().add("vdu");
		setOnMousePressed((evt) -> {
			var mouseMode = emulator.getModes().getMouseReport() != MouseReport.OFF; 
			if ((!mouseMode || (mouseMode && evt.isAltDown())) && evt.isPopupTrigger()) {
				contextMenu.show(term, evt.getScreenX(), evt.getScreenY());
				evt.consume();
			} else {
				contextMenu.hide();
			}
			
		});
		setOnMouseReleased((evt) -> {
			var mouseMode = emulator.getModes().getMouseReport() != MouseReport.OFF; 
			if (!mouseMode && evt.isPopupTrigger()) {
				contextMenu.show(term, evt.getScreenX(), evt.getScreenY());
				evt.consume();
			} 
		});

		scroller = new PrettyScrollBar(terminalPanel);
		scroller.setAutoHide(false);
		scroller.getNativeComponent().setOnMouseEntered(evt -> {
			inScrollBar = true;
			if(getScrollBar() == ScrollBarMode.AUTOMATIC) {
				fadeInScrollBar(false);
			}
		});
		scroller.getNativeComponent().setOnMouseExited(evt -> {
			inScrollBar = false;
			fadeOutScrollBar();
		});
		
		
		/* It seems rather unfortunately JavaFX does not present events in consistent way across
		 * operating systems. 
		 * 
		 * Here, we capture the key presses, and if they match an accelerator we store it, but don't
		 * yet action it.
		 * 
		 * When a key release of the same code (or character) occurs, but any modifier state, we then
		 * execute that action. 
		 */
		terminalPanel.setOnBeforeKeyPressed(evt -> {
			for (var action : actions(ttyContext, on)) {
				if (action.hasAccelerator() && action.accelerator().match(evt)) {
					if (action.accelerator() instanceof KeyCodeCombination kcc) {
						consumed.put(kcc.getCode(), action);
					} else if (action.accelerator() instanceof KeyCharacterCombination kcc) {
						consumed.put(kcc.getCharacter(), action);
					}
					evt.consume();
					break;
				}
			}
		});
		terminalPanel.setOnBeforeKeyReleased(evt -> {
			for (var action : actions(ttyContext, on)) {
				if (action.accelerator() instanceof KeyCodeCombination kcc) {
					if (consumed.remove(kcc.getCode()) != null) {
						LOG.info("Accelerated keyrelease action {} activated by {}", action.id(),
								action.accelerator().getDisplayText());
						shell.execute(action.fullCommand());
						evt.consume();
					}
				} else if (action.accelerator() instanceof KeyCharacterCombination kcc) {
					if (consumed.remove(kcc.getCharacter()) != null) {
						LOG.info("Accelerated keyrelease action {} activated by {}", action.id(),
								action.accelerator().getDisplayText());
						shell.execute(action.fullCommand());
						evt.consume();
					}
				}
			}
		});
		terminalPanel.setOnBeforeKeyTyped(evt -> {
			if (consumed.isEmpty()) {
				for (var action : actions(ttyContext, on)) {
					if (action.hasAccelerator() && action.accelerator().match(evt)) {
						LOG.info("Accelerated action {} activated by {}", action.id(),
								action.accelerator().getDisplayText());
						shell.execute(action.fullCommand());
						evt.consume();
						break;
					}
				}
			} else {
				/*
				 * This is all we can do really, at least without trying to translate key typed
				 * events in key presses (i.e. key codes rather than strings).
				 */
				evt.consume();
			}
		});
		
		termArea = new StackPane();
		termArea.getChildren().add(term);
		termArea.getChildren().add(scroller.getNativeComponent());
		StackPane.setAlignment(scroller.getNativeComponent(), Pos.CENTER_RIGHT);
		setCenter(termArea);
	}

	protected List<Action> actions(TTYContext ttyContext, On on) {
		return ttyContext.getContainer().getActions().actions().stream().filter(act -> act.on(on)).toList();
	}

	public void setScrollBar(ScrollBarMode state) {
		var sb = scroller.getNativeComponent();
		while(sb.getStyleClass().remove("auto-hide"));
		if(emulator.getScrollbackSize() == 0) {
			sb.setVisible(false);
			termArea.getChildren().remove(sb);
			setRight(null);
		}
		else {
			switch(state) {
			case AUTOMATIC:
				setRight(null);
				termArea.getChildren().remove(sb);
				termArea.getChildren().add(sb);
				sb.setVisible(true);
				sb.setOpacity(0);
				fadeInScrollBar(true);
				sb.getStyleClass().add("auto-hide");
				break;
			case PERMANENT:
				termArea.getChildren().remove(sb);
				setRight(scroller.getNativeComponent());
				sb.setVisible(true);
				sb.setOpacity(0);
				fadeInScrollBar(false);
				sb.getStyleClass().add("permanent");
				break;
			default:
				setRight(null);
				sb.setVisible(false);
				termArea.getChildren().remove(sb);
				break;
			}
		}
	}
	
	public ScrollBarMode getScrollBar() {
		if(termArea.getChildren().contains(scroller.getNativeComponent())) {
			return ScrollBarMode.AUTOMATIC;
		}
		else if(getRight() == scroller.getNativeComponent()) {
			return ScrollBarMode.PERMANENT;
		}
		else {
			return ScrollBarMode.HIDDEN;
		}
	}

	private void fadeInScrollBar(boolean temp) {
		var sb = scroller.getNativeComponent();
		stopFade();
		
		var fadeIn = new FadeTransition(Duration.millis(125));
		fadeIn.setFromValue(sb.getOpacity());
		fadeIn.setToValue(1f);
		fadeIn.setInterpolator(Interpolator.EASE_BOTH);
		
		if(temp) {
			var fadeOut = new FadeTransition(Duration.millis(500));
			fadeOut.setFromValue(1f);
			fadeOut.setToValue(0);
			fadeOut.setInterpolator(Interpolator.EASE_BOTH);
			fadeOut.setOnFinished(evt -> {
				
			});
			
			fading = new SequentialTransition(sb, fadeIn, new PauseTransition(Duration.seconds(1)), fadeOut);
		}
		else {			
			fadeIn.setNode(sb);
			fading = fadeIn;
		}
		
		fading.play();
		
		
	}

	private void fadeOutScrollBar() {
		stopFade();

		var sb = scroller.getNativeComponent();
		var fadeOut = new FadeTransition(Duration.millis(500));
		fadeOut.setNode(sb);
		fadeOut.setFromValue(sb.getOpacity());
		fadeOut.setToValue(0);
		fadeOut.setInterpolator(Interpolator.EASE_BOTH);

		fading = new SequentialTransition(sb, new PauseTransition(Duration.seconds(1)), fadeOut);
		
		fading.play();
		
		
	}

	private void stopFade() {
		if(fading != null) {
			fading.stop();
		}
	}
}
