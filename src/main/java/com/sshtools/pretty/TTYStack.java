package com.sshtools.pretty;

import static com.sshtools.jajafx.FXUtil.maybeQueue;
import static javafx.application.Platform.runLater;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.Data.Handle;

import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabDragPolicy;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public final class TTYStack extends StackPane implements TTYContext {
	private final static Logger LOG = LoggerFactory.getLogger(PrettyApp.class);
	private final static ResourceBundle RESOURCES = ResourceBundle.getBundle(TTYStack.class.getName());

	private final AppContext appContext;
	private final Stage stage;
	private PrettyAppWindow appWindow;
	
	private final static ThreadLocal<Boolean> detaching = new ThreadLocal<>();

	TTYStack(AppContext appContext, Stage stage) {

		setId("terminal-tabs");

		this.appContext = appContext;
		this.stage = stage;

		this.stage.setOnCloseRequest(evt -> {
			for (var tty : ttys()) {
				if (!maybeClose(tty)) {
					evt.consume();
					return;
				}
			}
		});
		updateStageTitle();
		if(!isDetaching())
			newTab();
	}

	protected static boolean isDetaching() {
		return Boolean.TRUE.equals(detaching.get());
	}

	@Override
	public Node content() {
		return this;
	}

	@Override
	public PrettyAppWindow appWindow() {
		return appWindow;
	}

	public void setAppWindow(PrettyAppWindow appWindow) {
		this.appWindow = appWindow;
	}

	public void select(TTY tty) {
		if (!getChildren().isEmpty()) {
			var first = getChildren().get(0);
			if (first instanceof TTY && tty.equals(first)) {
				return;
			} else if (first instanceof TabPane) {
				var tabPane = (TabPane) first;
				tabPane.getSelectionModel().select(tabPane.getTabs().get(indexOf(tty)));
				return;
			}
		}
		throw new IllegalStateException();
	}

	public int indexOf(TTY tty) {
		if (!getChildren().isEmpty()) {
			var first = getChildren().get(0);
			if (first instanceof TTY && tty.equals(first)) {
				return 0;
			} else if (first instanceof TabPane) {
				var tabPane = (TabPane) first;
				var index = 0;
				for (var tab : tabPane.getTabs()) {
					var t = (TTY) tab.getUserData();
					if (t.equals(tty)) {
						return index;
					}
					index++;
				}
			}
		}
		return -1;
	}

	@Override
	public void detachTab(TTY tty) {
		if (getChildren().size() > 0) {
			var first = getChildren().get(0);
			if (!(first instanceof TTY && tty.equals(first) ) ) {
				var tabPane = (TabPane) first;
				for(var tab : tabPane.getTabs()) {
					if(tab.getUserData().equals(tty)) {
						detaching.set(true);
						try {
							var newWnd = newWindow();
							closeTty(tty);
							var newCtx = (TTYStack)newWnd.ttyContext();
							newCtx.getChildren().add(tty);
							newCtx.updateStageTitle();
							newCtx.stage.sizeToScene();
							newCtx.stage.show();
							return;
						}
						finally {
							detaching.set(false);
						}
					}
				}
			}
		}
		throw new IllegalStateException("Not attached.");
	}

	public List<TTY> ttys() {
		if (getChildren().isEmpty())
			return Collections.emptyList();
		else {
			var first = getChildren().get(0);
			if (first instanceof TTY) {
				return Arrays.asList((TTY) first);
			} else {
				var tabPane = (TabPane) first;
				return tabPane.getTabs().stream().map(t -> (TTY) t.getUserData()).toList();
			}
		}
	}

	@Override
	public AppContext getContainer() {
		return appContext;
	}

	@Override
	public void newTab() {

		try {
			var tty = newTty();
			if (getChildren().isEmpty()) {
				getChildren().add(tty);
			} else {
				var first = getChildren().get(0);
				if (!(first instanceof TabPane)) {
					var firstTty = (TTY) first;
					getChildren().remove(firstTty);
					var firstTab = new Tab(firstTty.shortTitle().get(), firstTty);
					firstTty.shortTitle().addListener((c, o, n) -> {
						firstTab.setText(n);
						updateStageTitle();
						// TODO remove listener on tab remove / hiding to single
					});
					firstTab.setUserData(firstTty);

					var tabs = new TabPane(firstTab);
					tabs.setTabDragPolicy(TabDragPolicy.REORDER);
					var hndl = getContainer().getConfiguration().bindEnum(Side.class, tabs::setSide, tabs::getSide,
							"tabs", Constants.TERMINAL_SECTION);
					tabs.setUserData(hndl);
					getChildren().add(tabs);

					tabs.getSelectionModel().selectedItemProperty().addListener((c, o, n) -> {
						var selTty = (TTY) n.getUserData();
						updateStageTitle();
						runLater(() -> selTty.terminal().focusTerminal());
					});
					first = tabs;
				}

				var newTab = new Tab(tty.shortTitle().get(), tty);
				newTab.setContextMenu(createTabMenu(tty));
				newTab.setOnCloseRequest(evt -> {
					if (!maybeClose(tty)) {
						evt.consume();
						return;
					}
				});
				tty.shortTitle().addListener((c, o, n) -> {
					newTab.setText(n);
					updateStageTitle();
					// TODO remove listener on tab remove / hiding to single
				});
				newTab.setUserData(tty);

				var tabPane = (TabPane) first;
				tabPane.getTabs().add(newTab);
				tabPane.getSelectionModel().select(newTab);
			}
			updateStageTitle();
		} catch (Exception e) {
			LOG.error("Failed to add tab.", e);
		}
	}

	@Override
	public PrettyAppWindow newWindow() {
		var stage = new Stage();
		var wnmd = appContext.newAppWindow(stage);
		if(!isDetaching()) {
			stage.sizeToScene();
			stage.show();
		}
		return wnmd;
	}

	@Override
	public Stage stage() {
		return stage;
	}

	private ContextMenu createTabMenu(TTY tty) {
		var menu = new ContextMenu();
		var detachMenuItem = new MenuItem(RESOURCES.getString("detach"));
		detachMenuItem.setOnAction((e) -> {
			detachTab(tty);
		});
		menu.getItems().addAll(detachMenuItem);
		return menu;
	}

	private boolean maybeClose(TTY tty) {
		var canClose = tty.canClose();
		if (canClose.isPresent()) {
			select(tty);

			var alert = new Alert(AlertType.CONFIRMATION);
			alert.initOwner(stage);
			alert.setTitle(RESOURCES.getString("closeTitle"));
			alert.setHeaderText(canClose.get());
			alert.setContentText(RESOURCES.getString("closeText"));

			var close = new ButtonType(RESOURCES.getString("close"));
			var cancel = new ButtonType(RESOURCES.getString("cancel"), ButtonData.CANCEL_CLOSE);

			alert.getButtonTypes().setAll(close, cancel);

			var result = alert.showAndWait();
			if (result.get() == close) {
				tty.close();
			} else {
				return false;
			}
		} else {
			tty.close();
		}
		return true;
	}

	private void updateStageTitle() {
		activeTty().ifPresentOrElse(tty -> {
			stage.setTitle(tty.title().get());
		}, () -> {
			stage.setTitle(RESOURCES.getString("title"));
		});
	}

	@Override
	public Optional<TTY> activeTty() {
		if (getChildren().isEmpty())
			return Optional.empty();
		else {
			var first = getChildren().get(0);
			if (first instanceof TTY) {
				return Optional.of((TTY) first);
			} else {
				var tabPane = (TabPane) first;
				var sel = tabPane.getSelectionModel().getSelectedItem();
				var tty = (TTY) sel.getUserData();
				return Optional.of(tty);
			}
		}
	}

	private void closeTty(TTY tty) {
		/* TODO remove listeners */
		maybeQueue(() -> {
			var ttys = ttys();
			var idx = ttys.indexOf(tty);
			if (idx > -1) {
				var first = getChildren().get(0);
				if (first instanceof TabPane) {
					/*
					 * Now just a single tab, switch back to adding the tty directly to the frames
					 * stack
					 */
					var tabPane = (TabPane) first;
					if(tabPane.getTabs().size() == 2) {
						((Handle) tabPane.getUserData()).close();
						var firstTab = tabPane.getTabs().get(0);
						getChildren().remove(first);
						getChildren().add((TTY) firstTab.getUserData());
					}
					else {
						var tab = tabPane.getTabs().get(idx);
						tabPane.getTabs().remove(tab);		
					}
				} else {
					stage.close();
				}
				updateStageTitle();
			}
		});
	}

	private TTY newTty() {
		var tty = new TTY(this, this::closeTty);
		// TODO remove listener on tab remove / hiding to single
		tty.title().addListener((c, o, n) -> updateStageTitle());
		return tty;
	}
}