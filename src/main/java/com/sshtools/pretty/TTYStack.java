package com.sshtools.pretty;

import static com.sshtools.jajafx.FXUtil.maybeQueue;
import static javafx.application.Platform.runLater;

import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

import org.jline.reader.impl.history.DefaultHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jajafx.SplitTabPane;
import com.sshtools.jini.Data.Handle;
import com.sshtools.pretty.Actions.Action;
import com.sshtools.pretty.Actions.On;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane.TabDragPolicy;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public final class TTYStack extends StackPane implements TTYContext, ListChangeListener<Action> {
	private final static Logger LOG = LoggerFactory.getLogger(TTYStack.class);
	private final static ResourceBundle RESOURCES = ResourceBundle.getBundle(TTYStack.class.getName());

	private final AppContext appContext;
	private final Stage stage;
	private PrettyAppWindow appWindow;
	private final ObservableList<Action> actions;
	private final SplitTabPane tabs;
	private Handle hndl;
	private final HistoryConfig usernameHistory;
	private final HistoryConfig hostnameHistory;

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
		
		usernameHistory = new HistoryConfig(getContainer().getConfiguration().dir().resolve("usernames.history"), 100, new DefaultHistory());
		hostnameHistory = new HistoryConfig(getContainer().getConfiguration().dir().resolve("hostname.history"), 100, new DefaultHistory());
		
		actions = appContext.getActions().actions();
		actions.addListener(this);
		
		tabs = new SplitTabPane();
		tabs.setTabDragPolicy(TabDragPolicy.REORDER);
		hndl = getContainer().getConfiguration().bindEnum(Side.class, tabs::setSide, tabs::getSide,
				"tabs", Constants.TERMINAL_SECTION);
		tabs.getSelectionModel().selectedItemProperty().addListener((c, o, n) -> {
			if(n == null)
				return;
			var selTty = (TTY) n.getUserData();
			updateStageTitle();
			runLater(() -> selTty.terminal().focusTerminal());
		});
		
		getChildren().add(tabs);
		
		updateStageTitle();
	}

	@Override
	public HistoryConfig usernameHistory() {
		return usernameHistory;
	}

	@Override
	public HistoryConfig hostnameHistory() {
		return hostnameHistory;
	}

	@Override
	public Optional<TTY> activeTty() {
//		if (getChildren().isEmpty())
//			return Optional.empty();
//		else {
//			var first = getChildren().get(0);
//			if (first instanceof TTY) {
//				return Optional.of((TTY) first);
//			} else {
//				var tabPane = (TabPane) first;
//				var sel = tabPane.getSelectionModel().getSelectedItem();
//				var tty = (TTY) sel.getUserData();
//				return Optional.of(tty);
//			}
//		}
		return Optional.ofNullable(tabs.getSelectedTab()).map(t -> (TTY) t.getUserData());
	}

	@Override
	public PrettyAppWindow appWindow() {
		return appWindow;
	}

	@Override
	public void close() {
		ttys().forEach(tty -> tty.close());
		actions.removeListener(this);
		hndl.close();
		
	}

	@Override
	public Node content() {
		return this;
	}

	@Override
	public void detachTab(TTY tty) {
		tabs.getTabs().stream().filter(t -> t.getUserData().equals(tty)).findFirst().ifPresentOrElse(tab -> {
			var newWnd = newWindow(Optional.empty());
			closeTty(tty);
			var newCtx = (TTYStack)newWnd.ttyContext();
			newCtx.getChildren().add(tty);
			newCtx.updateStageTitle();
			newCtx.stage.sizeToScene();
			newCtx.stage.show();
		}, () -> { 
			throw new IllegalStateException("Not attached."); 
		});
	}

	@Override
	public AppContext getContainer() {
		return appContext;
	}

	public int indexOf(TTY tty) {
		return tabs.getTabs().indexOf(tabs.getTabs().stream().filter(t -> t.getUserData().equals(tty)).findFirst().orElse(null));
//		if (!getChildren().isEmpty()) {
//			var first = getChildren().get(0);
//			if (first instanceof TTY && tty.equals(first)) {
//				return 0;
//			} else if (first instanceof TabPane) {
//				var tabPane = (TabPane) first;
//				var index = 0;
//				for (var tab : tabPane.getTabs()) {
//					var t = (TTY) tab.getUserData();
//					if (t.equals(tty)) {
//						return index;
//					}
//					index++;
//				}
//			}
//		}
//		return -1;
	}

	@Override
	public void newTab(TTYRequest request) {

		try {
			LOG.info("New tab in stack {}", hashCode());
			
			var tty = newTty(request);
//			if (getChildren().isEmpty()) {
//				getChildren().add(tty);
//			} else {
//				var first = getChildren().get(0);
//				if (!(first instanceof TabPane)) {
//					var firstTty = (TTY) first;
//					getChildren().remove(firstTty);
//					var firstTab = createTabForTty(firstTty);
//
//					var tabs = new TabPane(firstTab);
//					tabs.setTabDragPolicy(TabDragPolicy.REORDER);
//					var hndl = getContainer().getConfiguration().bindEnum(Side.class, tabs::setSide, tabs::getSide,
//							"tabs", Constants.TERMINAL_SECTION);
//					tabs.setUserData(hndl);
//					getChildren().add(tabs);
//
//					tabs.getSelectionModel().selectedItemProperty().addListener((c, o, n) -> {
//						var selTty = (TTY) n.getUserData();
//						updateStageTitle();
//						runLater(() -> selTty.terminal().focusTerminal());
//					});
//					first = tabs;
//				}
//
//				var newTab = createTabForTty(tty);
//
//				var tabPane = (TabPane) first;
//				tabPane.getTabs().add(newTab);
//				tabPane.getSelectionModel().select(newTab);
//			}
			

			var newTab = createTabForTty(tty);
			tabs.getTabs().add(newTab);
			tabs.getSelectionModel().select(newTab);
			
			
			updateStageTitle();
		} catch (Exception e) {
			LOG.error("Failed to add tab.", e);
		}
	}

	@Override
	public void splitTab(TTY other, TTYRequest request, Orientation orientation) {
		try {
			LOG.info("New tab in stack {}", hashCode());
			
			var otherTab = findTabForTty(other);
			var tty = newTty(request);

			var newTab = createTabForTty(tty);
			tabs.splitTab(otherTab, newTab, orientation);
			tabs.getSelectionModel().select(newTab);
			
			
			updateStageTitle();
		} catch (Exception e) {
			LOG.error("Failed to add tab.", e);
		}
		
	}

	@Override
	public PrettyAppWindow newWindow(Optional<TTYRequest> request) {
		var stage = new Stage();
		var wnmd = appContext.newAppWindow(stage, request);
		if(request.isPresent()) {
			stage.sizeToScene();
			stage.show();
		}
		return wnmd;
	}

	@Override
	public void onChanged(Change<? extends Action> c) {
		reloadAccelerators();
	}

	@Override
	public void renameTab(TTY tty) {
		renameTab(tty, findTabForTty(tty));
	}

	public void select(TTY tty) {
//		if (!getChildren().isEmpty()) {
//			var first = getChildren().get(0);
//			if (first instanceof TTY && tty.equals(first)) {
//				return;
//			} else if (first instanceof TabPane) {
//				var tabPane = (TabPane) first;
//				tabPane.getSelectionModel().select(tabPane.getTabs().get(indexOf(tty)));
//				return;
//			}
//		}
//		throw new IllegalStateException();
		tabs.getSelectionModel().select(indexOf(tty));
	}

	public void setAppWindow(PrettyAppWindow appWindow) {
		this.appWindow = appWindow;
		reloadAccelerators();
	}

	@Override
	public Stage stage() {
		return stage;
	}

	public List<TTY> ttys() {
//		if (getChildren().isEmpty())
//			return Collections.emptyList();
//		else {
//			var first = getChildren().get(0);
//			if (first instanceof TTY) {
//				return Arrays.asList((TTY) first);
//			} else {
//				var tabPane = (TabPane) first;
//				return tabPane.getTabs().stream().map(t -> (TTY) t.getUserData()).toList();
//			}
//		}

		return tabs.getTabs().stream().map(t -> (TTY) t.getUserData()).toList();
	}

	protected Tab createTabForTty(TTY tty) {
		var newTab = new Tab(tty.shortTitle().get(), tty);
		newTab.setContextMenu(createTabMenu(tty, newTab));
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
		tty.tabColor().addListener((c,o,n) -> updateTabColor(newTab, n));
		updateTabColor(newTab, tty.tabColor().get());
		newTab.setUserData(tty);
		return newTab;
	}
	
	private void closeTty(TTY tty) {
		/* TODO remove listeners */
		maybeQueue(() -> {
			tabs.getTabs().remove(ttys().indexOf(tty));
			if(tabs.getTabs().isEmpty()) {
				/* No more tabs, remove the tab pane and add the tty directly to the stack */
				stage.close();
			}
			updateStageTitle();
			
			
//			if (idx > -1) {
//				var first = getChildren().get(0);
//				if (first instanceof TabPane) {
//					/*
//					 * Now just a single tab, switch back to adding the tty directly to the frames
//					 * stack
//					 */
//					var tabPane = (TabPane) first;
//					if(tabPane.getTabs().size() == 2) {
//						((Handle) tabPane.getUserData()).close();
//						var firstTab = tabPane.getTabs().get(0);
//						getChildren().remove(first);
//						getChildren().add((TTY) firstTab.getUserData());
//					}
//					else {
//						var tab = tabPane.getTabs().get(idx);
//						tabPane.getTabs().remove(tab);		
//					}
//				} else {
//					stage.close();
//				}
//				updateStageTitle();
//			}
		});
	}

	private ContextMenu createTabMenu(TTY tty, Tab tab) {
		var menu = new ActionsContextMenu(On.TAB, getContainer().getActions(), () -> tty.cli().shell()) {

			@Override
			protected void onRebuild() {
				try {
					getItems().add(new SeparatorMenuItem());
					
					var thm = tty.ttyContext().getContainer().getThemes().get("material-dark").get();
					var pal = thm.pal16().get();
					var tg = new ToggleGroup();
					var current = tty.tabColor().get();

					var defaultCol = new RadioMenuItem(RESOURCES.getString("defaultColor"));
					defaultCol.setToggleGroup(tg);
					defaultCol.setOnAction(evt -> {
						tty.tabColor().set(null);
					});
					
					getItems().add(defaultCol);
					tg.selectToggle(null);
					
					for(int i = 0 ; i < 16 ; i++) {
						var col = tty.terminal().getUIToolkit().localColorToNativeColor(pal[i]);
						var item = new RadioMenuItem("● " + i);
						item.setToggleGroup(tg);
						item.setOnAction(a -> {
							tty.tabColor().set(col);
						});
						item.setStyle("-fx-text-fill: " + Colors.toHex(col));
						getItems().add(item);
						
						if(col.equals(current)) {
							tg.selectToggle(item);
						}
					}

					if(tg.getSelectedToggle() == null) {
						tg.selectToggle(defaultCol);
					}
				}
				catch(Exception e) {
					
				}
			}
			
		};
		
		return menu;
	}

	private Tab findTabForTty(TTY tty) {
		return tabs.getTabs().stream().filter(t -> t.getUserData().equals(tty)).findFirst().orElseThrow(() -> new IllegalArgumentException("No tab for this tty."));
//		var first = getChildren().get(0);
//		if (!(first instanceof TTY)) {
//			var tabPane = (TabPane) first;
//			for(var tab : tabPane.getTabs()) {
//				if(tab.getUserData().equals(tty)) {
//					return tab;
//				}
//			}
//		}
//		throw new IllegalArgumentException("No tab for this tty.");
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

	private TTY newTty(TTYRequest request) {
		var tty = new TTY.Builder(request.name(), this).onClose(this::closeTty).withRequest(request).build();
		// TODO remove listener on tab remove / hiding to single
		tty.title().addListener((c, o, n) -> updateStageTitle());
		return tty;
	}

	@Deprecated
	private void reloadAccelerators() {
//		stage.getScene().getAccelerators().clear();
//		actions.filtered(Action::hasAccelerator).forEach(act ->{ 
//			stage.getScene().getAccelerators().put(act.accelerator(), () -> {
//				LOG.info("Accelerated action {} activated by {}", act.id(), act.accelerator().getDisplayText());
//				activeTty().ifPresent(tty -> { 
//					tty.cli().shell().execute(act.fullCommand()); 
//				});
//			});
//		});
	}

	private void renameTab(TTY tty, Tab tab) {
		var wasContent = tab.getGraphic();
		var txt = new TextField();
		txt.setText(tab.getText());
		txt.selectAll();
		tab.setText("");
		
		Runnable task = () -> {
			tab.setGraphic(wasContent);
			var newTxt = txt.getText();
			tab.setText(newTxt);
			tty.userTitle().set(newTxt);
			tty.terminal().focusTerminal();
		};
		
		txt.setOnKeyReleased(evt -> {
			if(evt.getCode() == KeyCode.ENTER || evt.getCode() == KeyCode.ESCAPE || evt.getCode() == KeyCode.TAB) {
				task.run();
			}
		});
		txt.focusedProperty().addListener((c,o,n) -> {
			if(!n) {
				task.run();
			}
		});
		tab.setGraphic(txt);
		txt.requestFocus();
	}

	private void updateStageTitle() {
		activeTty().ifPresentOrElse(tty -> {
			stage.setTitle(tty.title().get());
		}, () -> {
			stage.setTitle(RESOURCES.getString("title"));
		});
	}

	private void updateTabColor(Tab tab, Color color) {
		if(color == null)
			tab.setStyle(null);
		else {
			var sc = tab.getStyleClass();
			sc.remove("dark-tab");
			sc.remove("light-tab");
			if(Colors.bestWithDarkForeground(color)) {
				sc.add("light-tab");
			}
			else {
				sc.add("dark-tab");
			}
			tab.setStyle("-fx-background-color: " + Colors.toHex(color) + " !important;");
		}
	}
}