package com.sshtools.pretty;

import static com.sshtools.jajafx.FXUtil.maybeQueue;
import static javafx.application.Platform.runLater;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
//import org.scenicview.ScenicView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jajafx.FXUtil;
import com.sshtools.jajafx.JajaFXApp;
import com.sshtools.jaul.UpdateService;
import com.sshtools.jini.Data.Handle;
import com.sshtools.terminal.emulation.UIToolkit;
import com.sshtools.terminal.emulation.fonts.FontManager;
import com.sshtools.terminal.fonts.EmojiFonts;
import com.sshtools.terminal.vt.javafx.JavaFXUIToolkit;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabDragPolicy;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Pair;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;
import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.Resource;
@Bundle
@Resource({"themes.properties",
	"themes/black-on-light-yellow.properties",
	"themes/black-on-white.properties",
	"themes/green-on-black.properties",
	"themes/grey-on-black.properties",
	"themes/solarized-dark.properties",
	"themes/solarized-light.properties",
	"themes/tango-dark.properties",
	"themes/tango-light.properties",
	"themes/white-onblack.properties",
	})
@Reflectable
public class PrettyApp extends JajaFXApp<Pretty> {

	private final static Logger LOG = LoggerFactory.getLogger(PrettyApp.class);
	private final static ResourceBundle RESOURCES = ResourceBundle.getBundle(PrettyApp.class.getName());

	private final JavaFXUIToolkit uiToolkit;
	private final FontManager<Font> fontManager;
	private final AppContextImpl appContext;

	private boolean optionsVisible;

	public PrettyApp() {
		super(PrettyApp.class.getResource("icon.png"), RESOURCES.getString("title"), (Pretty) Pretty.getInstance());
		uiToolkit = new JavaFXUIToolkit();
		fontManager =new FontManager<>(uiToolkit);
		appContext = new AppContextImpl();
		new EmojiFonts<Font>(fontManager);
	}

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	protected void needUpdate() {
		maybeQueue(() -> {
			/* TODO */
		});
	}

	@Override
	protected void onStarted() {
		/* TODO weird */
		getPrimaryStage().sizeToScene();
	}	
	
	@Override
	protected void onConfigureStage(Stage stage) {
		LOG.info("Configuring stage, sizing to scene");
		stage.sizeToScene();
		LOG.info("Configuring stage, sized to scene at {} x {}", stage.getWidth(), stage.getHeight());
//		ScenicView.show(getPrimaryStage().getScene());
	}
	

	@Override
	protected Node createContent() {
		return new TTYContextImpl(appContext, getPrimaryStage());
	}
	
	@Override
	protected void updateDarkMode() {
		super.updateDarkMode();
	}

	@Override
	public void addCommonStylesheets(ObservableList<String> stylesheets) {
		super.addCommonStylesheets(stylesheets);
		var appResource = getClass().getResource("Pretty.css");
		if(appResource != null) {
			FXUtil.addIfNotAdded(stylesheets, appResource.toExternalForm());
		}
	}

	class AppContextImpl implements AppContext {

		@Override
		public void options(Stage owner) {

			if (optionsVisible)
				return;
			optionsVisible = true;
			Platform.runLater(() -> {
				try {
					var options = new Options(this);

					var dialog = new Dialog<Pair<String, String>>();

					dialog.setHeaderText(RESOURCES.getString("optionsText"));
					dialog.initOwner(owner);

					var fi = new FontIcon();
					fi.setIconCode(FontAwesomeSolid.COGS);
					fi.setIconSize(48);
					dialog.setGraphic(fi);

					var scene = dialog.getDialogPane();
					var window = dialog.getDialogPane().getScene().getWindow();
					dialog.setTitle(" ");
					window.setOnCloseRequest(event -> window.hide());
					applyStylesToRoot(window.getScene().getRoot());

					scene.setContent(options);
					try {
						dialog.showAndWait();
					} finally {
						options.close();
						optionsVisible = false;
					}
				} catch (Exception e) {
					LOG.error("Failed to show options.", e);
				}

			});
		}

		@Override
		public void addCommonStylesheets(ObservableList<String> stylesheets) {
			PrettyApp.this.addCommonStylesheets(stylesheets);
		}

		@Override
		public TerminalTheme getSelectedTheme() {
			var prefs = getContainer().getAppPreferences();
			return TerminalTheme.getTheme(prefs.get("theme", "LogonBox Blue"));
		}

		@Override
		public void about(Stage owner) {

			Platform.runLater(() -> {
				var options = new AboutDialog(this);
				var dialog = new Dialog<Pair<String, String>>();
				dialog.setDialogPane(options);
				dialog.setTitle(RESOURCES.getString("about"));
				dialog.initOwner(owner);
				var window = options.getScene().getWindow();
				window.setOnCloseRequest(event -> window.hide());
				dialog.showAndWait();
			});
			
		}

		@Override
		public Configuration getConfiguration() {
			return PrettyApp.this.getContainer().getConfiguration();
		}

		@Override
		public UIToolkit<Font, Color> getUiToolkit() {
			return uiToolkit;
		}

		@Override
		public FontManager<Font> getFontManager() {
			return fontManager;
		}

		@Override
		public UpdateService getUpdateService() {
			return PrettyApp.this.getContainer().getUpdateService();
		}

		@Override
		public Preferences getPreferences() {
			return getContainer().getAppPreferences();
		}

		@Override
		public HostServices getHostServices() {
			return PrettyApp.this.getHostServices();
		}

		@Override
		public boolean isDarkMode() {
			return PrettyApp.this.isDarkMode();
		}

		@Override
		public void updateDarkMode(JMetro jMetro, Parent parent) {
			PrettyApp.this.updateDarkMode(jMetro, parent);
		}

		@Override
		public URL getIcon() {
			return PrettyApp.this.getIcon();
		}
	}

	final static class TTYContextImpl extends StackPane implements TTYContext {
		
		private final AppContext appContext;
		private final Stage stage;

		TTYContextImpl(AppContext appContext, Stage stage) {
			
			setId("terminal-tabs");
			
			this.appContext = appContext;
			this.stage = stage;

			this.stage.setOnCloseRequest(evt -> {
				for(var tty : ttys()) {
					if(!maybeClose(tty)) {
						evt.consume();
						return;
					}
				}
			});
			updateStageTitle();
			newTab();
		}
		
		public void select(TTY tty) {
			if(!getChildren().isEmpty()) {
				var first = getChildren().get(0);
				if(first instanceof TTY && tty.equals(first)) {
					return;
				}
				else if(first instanceof TabPane) {
					var tabPane = (TabPane)first;
					tabPane.getSelectionModel().select(tabPane.getTabs().get(indexOf(tty)));
					return;
				}
			}
			throw new IllegalStateException();
		}
		
		public int indexOf(TTY tty) {
			if(!getChildren().isEmpty()) {
				var first = getChildren().get(0);
				if(first instanceof TTY && tty.equals(first)) {
					return 0;
				}
				else if(first instanceof TabPane) {
					var tabPane = (TabPane)first;
					var index = 0;
					for(var tab : tabPane.getTabs()) {
						var t = (TTY)tab.getUserData();
						if(t.equals(tty)) {
							return index;
						}
						index++;
					}
				}
			}
			return -1;
		}
		
		public List<TTY> ttys() {
			if(getChildren().isEmpty())
				return Collections.emptyList();
			else {
				var first = getChildren().get(0);
				if(first instanceof TTY) {
					return Arrays.asList((TTY)first);
				}
				else {
					var tabPane = (TabPane)first;
					return tabPane.getTabs().stream().map(t -> (TTY)t.getUserData()).toList();
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
				if(getChildren().isEmpty()) {
					getChildren().add(tty);
				}
				else {
					var first = getChildren().get(0); 
					if(!(first instanceof TabPane)) {
						var firstTty = (TTY)first;
						getChildren().remove(firstTty);
						var firstTab = new Tab(firstTty.shortTitle().get(), firstTty);
						firstTty.shortTitle().addListener((c,o,n) -> { 
							firstTab.setText(n); 
							updateStageTitle();
							// TODO remove listener on tab remove / hiding to single
						}); 
						firstTab.setUserData(firstTty);
						
						var tabs = new TabPane(firstTab);
						tabs.setTabDragPolicy(TabDragPolicy.REORDER);
						var hndl = getContainer().getConfiguration().bindEnum(Side.class, tabs::setSide, tabs::getSide, "tabs", Options.TERMINAL_SECTION);
						tabs.setUserData(hndl);
						getChildren().add(tabs);
						
						tabs.getSelectionModel().selectedItemProperty().addListener((c,o,n) ->  {
							var selTty = (TTY)n.getUserData();
							updateStageTitle();
							runLater(() -> selTty.terminal().focusTerminal());
						});
						first = tabs;
					}
					
					var newTab = new Tab(tty.shortTitle().get(), tty);
					newTab.setOnCloseRequest(evt -> {
						if(!maybeClose(tty)) {
							evt.consume();
							return;
						}
					});
					tty.shortTitle().addListener((c,o,n) -> { 
						newTab.setText(n); 
						updateStageTitle();
						// TODO remove listener on tab remove / hiding to single
					}); 
					newTab.setUserData(tty);
					
					var tabPane = (TabPane)first;
					tabPane.getTabs().add(newTab);
					tabPane.getSelectionModel().select(newTab);
				}
				updateStageTitle();
			}
			catch(Exception e) {
				LOG.error("Failed to add tab.", e);
			}
		}

		@Override
		public void newWindow() {
			var stage =new Stage(StageStyle.DECORATED);
			stage.getIcons().add(new Image(appContext.getIcon().toExternalForm()));
			
			var ttyContext = new TTYContextImpl(appContext, stage);
			var scene = new Scene(ttyContext);
			stage.setScene(scene);
			appContext.addCommonStylesheets(scene.getStylesheets());
			

			var jMetro = new JMetro(appContext.isDarkMode() ? Style.DARK : Style.LIGHT);
			appContext.updateDarkMode(jMetro, ttyContext);
			jMetro.setScene(scene);
			
			stage.sizeToScene();
			stage.show();
		}

		private boolean maybeClose(TTY tty) {
			var canClose = tty.canClose();
			if(canClose.isPresent()) {
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
			}
			else {
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
		
		private Optional<TTY> activeTty() {
			if(getChildren().isEmpty())
				return Optional.empty();
			else {
				var first = getChildren().get(0);
				if(first instanceof TTY) {
					return Optional.of((TTY)first);
				}
				else {
					var tabPane = (TabPane)first;
					var sel = tabPane.getSelectionModel().getSelectedItem();
					var tty = (TTY)sel.getUserData();
					return Optional.of(tty);
				}
			}
		}
		
		private void closeTty(TTY tty) {
			/* TODO remove listeners */
			maybeQueue(() -> {
				var ttys = ttys();
				var idx = ttys.indexOf(tty);
				if(idx > -1) {
					var first = getChildren().get(0);
					if(getChildren().size() == 1) {
						if(first instanceof TabPane) {
							/* Now just a single tab, switch back to adding the tty directly
							 * to the frames stack
							 */
							var tabPane = (TabPane)first;
							((Handle)tabPane.getUserData()).close();
							var firstTab = tabPane.getTabs().get(0);
							getChildren().remove(first);
							getChildren().add((TTY)firstTab.getUserData());
						}
						else {
							stage.close();
						}
					}
					else {
						var tabPane = (TabPane)first;
						var tab = tabPane.getTabs().get(idx);
						tabPane.getTabs().remove(tab);					
					}
					updateStageTitle();
				}
			});
		}

		private TTY newTty() {
			var tty = new TTY(this, this::closeTty);
			// TODO remove listener on tab remove / hiding to single
			tty.title().addListener((c,o,n) -> updateStageTitle());
			return tty;
		}


		@Override
		public Stage stage() {
			return stage;
		}
	}
}