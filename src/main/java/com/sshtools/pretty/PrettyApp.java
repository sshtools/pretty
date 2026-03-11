package com.sshtools.pretty;


import static com.sshtools.jajafx.FXUtil.maybeQueue;

import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.prefs.Preferences;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import org.scenicview.ScenicView;
//import org.scenicview.ScenicView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.install4j.api.launcher.StartupNotification;
import com.install4j.api.launcher.StartupNotification.Listener;
import com.sshtools.jajafx.FXUtil;
import com.sshtools.jajafx.JajaFXAppWindow;
import com.sshtools.jajafx.updateable.UpdateableJajaFXApp;
import com.sshtools.jajafx.updateable.UpdateableJajaFXAppWindow;
import com.sshtools.jaul.UpdateService;
import com.sshtools.jini.config.Monitor;
import com.sshtools.terminal.emulation.UIToolkit;
import com.sshtools.terminal.vt.javafx.JavaFXUIToolkit;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Reflectable;

@Bundle
@Reflectable
public class PrettyApp extends UpdateableJajaFXApp<Pretty, PrettyAppWindow> implements Listener {

	private final static Logger LOG = LoggerFactory.getLogger(PrettyApp.class);
	private final static ResourceBundle RESOURCES = ResourceBundle.getBundle(PrettyApp.class.getName());

	private final JavaFXUIToolkit uiToolkit;
	private final AppContextImpl appContext;
	private final Fonts fonts;

	private static final ThreadLocal<Optional<TTYRequest>> openingRequest = new ThreadLocal<>();

	public PrettyApp() {
		super(PrettyApp.class.getResource("icon.png"), 
		      RESOURCES.getString("title"), 
		      (Pretty) Pretty.getInstance(), ((Pretty) Pretty.getInstance()).getAppPreferences());
		Pretty.getInstance().init(this);
		uiToolkit = new JavaFXUIToolkit();
		appContext = new AppContextImpl();
		fonts = new Fonts(appContext, uiToolkit);
		setDefaultStandardWindowDecorations(false);
		setShowFrameTitle(true);
//		new EmojiFonts<Font>(fontManager);
		StartupNotification.registerStartupListener(this);
	}

	public static void main(String[] args) {
		launch(args);
	}
	
	@Override
	public void needUpdate() {
		maybeQueue(() -> getWindows().forEach(wnd -> {
			if(wnd instanceof PrettyAppWindow paw)
				paw.updateUpdatesState(); 
		}));
	}

	@Override
	public void addCommonStylesheets(ObservableList<String> stylesheets) {
		FXUtil.addIfNotAdded(stylesheets, appContext.customCSS().managedStyles().toUri().toString());
		var appResource = getClass().getResource("Pretty.css");
		if (appResource != null) {
			FXUtil.addIfNotAdded(stylesheets, appResource.toExternalForm());
		}
		super.addCommonStylesheets(stylesheets);
	}

	@Override
	public void start(final Stage primaryStage) {
		primaryStage.initStyle(StageStyle.DECORATED);
		super.start(primaryStage);
	}

	@Override
	protected void onStarted() {
		/* TODO weird */
		Window.getWindows().get(0).sizeToScene();
	}

	@Override
	protected void onConfigurePrimaryStage(JajaFXAppWindow<?> wnd, Stage stage) {
		LOG.info("Configuring stage, sizing to scene");
		stage.sizeToScene();
		LOG.info("Configuring stage, sized to scene at {} x {}", stage.getWidth(), stage.getHeight());
	}

	@Override
	protected TTYStack createContent(Stage stage, PrettyAppWindow window) {
		return new TTYStack(appContext, stage);
	}

	@Override
	protected DarkMode getDarkMode() {
		return appContext.getConfiguration().ui().getEnum(DarkMode.class, Constants.DARK_MODE_KEY);
	}

	@Override
	protected UpdateableJajaFXAppWindow<?> createAppWindow(Stage stage) {
		var aw = new PrettyAppWindow(stage, this, appContext);
		var ctx = createContent(stage, aw);
		aw.setContext(ctx);
		ctx.setAppWindow(aw);
		
		var req = openingRequest.get();
		if(req == null)
			ctx.newTab();
		else if(req.isPresent()) {
			ctx.newTab(req.get());
		} else
			ctx.newTab();
		
		return aw;
	}

	@Override
	public void startupPerformed(String parameters) {
		var cmdline = new CommandLine(new RemoteCommand(appContext));
		cmdline.setCaseInsensitiveEnumValuesAllowed(true);
		cmdline.execute(Strings.parseQuotedString(parameters).toArray(new String[0]));
//		
	}

	@Override
	protected void listenForDarkModeChanges() {
		appContext.getConfiguration().bindEnum(DarkMode.class, dm -> updateDarkMode(), null, Constants.DARK_MODE_KEY, Constants.UI_SECTION);
	}

	@Command(name = "pretty-remote", mixinStandardHelpOptions = true, description = "Remote control of pretty", versionProvider = Pretty.Version.class)
	public final static class RemoteCommand implements Callable<Integer> {
		
		@Option(names = { "-s", "--size" }, description = "Size of window, in the format <width>X<height>.")
		private String size;
	
		@Option(names = { "-c", "--cwd" }, description = "Working directory for local shells.")
		private Optional<Path> workingDirectory;

		private final AppContext appContext;

		public RemoteCommand(AppContext appContext) {
			this.appContext = appContext;
		}

		@Override
		public Integer call() throws Exception {
			Platform.runLater(() -> {
				var stage = new Stage();
				appContext.newAppWindow(stage);
				stage.sizeToScene();
				stage.show();
			});
			return 0;
		}
	}

	class AppContextImpl implements AppContext {

		private final Themes themes;
		private final Shells shells;
		private final Actions actions;
		private final Monitor monitor;
		private final SecretStorage passwords;
		private final Configuration configuration;
		private JajaFXAppWindow<?> optionsWindow;
		private JajaFXAppWindow<?> actionsOverviewWindow;
		private final CustomCSS customCSS;

		AppContextImpl() {
			monitor = new Monitor(scheduler());
			configuration  =new Configuration(monitor);
			themes = new Themes(this, monitor);
			shells = new Shells(this, monitor);
			actions = new Actions(this, monitor);
			passwords = new DefaultSecretStorage(this);
			customCSS = new CustomCSS(this);
			
		}

		@Override
		public List<JajaFXAppWindow<PrettyApp>> getWindows() {
			return PrettyApp.this.getWindows();
		}

		@Override
		public CustomCSS customCSS() {
			return customCSS;
		}

		@Override
		public SecretStorage passwords() {
			return passwords;
		}

		@Override
		public ScheduledExecutorService scheduler() {
			return PrettyApp.this.getContainer().getScheduler();
		}

		@Override
		public Monitor monitor() {
			return monitor;
		}

		@Override
		public Shells getShells() {
			return shells;
		}

		@Override
		public Actions getActions() {
			return actions;
		}

		@Override
		public PrettyAppWindow newAppWindow(Stage stage, Optional<TTYRequest> request) {
			var was =  openingRequest.get();
			openingRequest.set(request);
			try {
				return PrettyApp.this.newAppWindow(stage);
			}
			finally {
				openingRequest.set(was);
			}
		}

		@Override
		public void options(Stage owner) {

			Platform.runLater(() -> {
				if(optionsWindow == null) {
					var options = new Options(this);
					
					var stg = new Stage();
					stg.initOwner(owner);
					stg.setOnCloseRequest(eh -> {
						optionsWindow = null;
						stg.close();
					});
					optionsWindow = new JajaFXAppWindow<>(stg, PrettyApp.this, 640, 680);
					optionsWindow.setContent(options);

					stg.getIcons().add(new Image(appContext.getIcon().toExternalForm()));
					stg.setResizable(false);
					stg.setTitle(RESOURCES.getString("optionsText"));
					stg.show();
					try {
						if(Boolean.getBoolean("jaja.debugScene"))
							ScenicView.show(stg.getScene());
					}
					catch(Throwable e) {
					}					
				}
				else if(optionsWindow.stage().isShowing()) {
					optionsWindow.stage().toFront();
				}
				else  {
					optionsWindow.stage().show();
				}
			});
		}

		@Override
		public void addCommonStylesheets(ObservableList<String> stylesheets) {
			PrettyApp.this.addCommonStylesheets(stylesheets);
		}

		@Override
		public TerminalTheme getSelectedTheme() {
			return themes.resolve(configuration.terminal().get(Constants.THEME_KEY));
		}

		@Override
		public void about(Stage owner) {
			Platform.runLater(() -> {
					var actions = new AboutPane(this);
					
					var stg = new Stage();
					var wnd = new JajaFXAppWindow<>(stg, PrettyApp.this, 400, 400);
					wnd.setContent(actions);
					wnd.scene().getRoot().setId("about-dialog");

					stg.initOwner(owner);
					stg.initModality(Modality.APPLICATION_MODAL);

					stg.getIcons().add(new Image(appContext.getIcon().toExternalForm()));
					stg.setResizable(false);
					stg.setTitle(RESOURCES.getString("about"));
					try {
						if(Boolean.getBoolean("jaja.debugScene"))
							ScenicView.show(stg.getScene());
					}
					catch(Throwable e) {
					}
					stg.showAndWait();
			});
			
		}

		@Override
		public void actions(Optional<String> filter, Stage owner) {
			
			Platform.runLater(() -> {
				if(actionsOverviewWindow == null) {
					var actions = new ActionsPane(this, filter);
					
					var stg = new Stage();
					actionsOverviewWindow = new JajaFXAppWindow<>(stg, PrettyApp.this, 520, 600);
					actionsOverviewWindow.setContent(actions);
					stg.setOnCloseRequest(eh -> {
						actionsOverviewWindow = null;
						stg.close();
					});
					stg.initOwner(owner);
					
					var toggleSearch = new FontIcon();
					toggleSearch.setIconSize(18);
					toggleSearch.setOnMouseClicked(evt -> {
						actions.searchVisibleProperty().set(!actions.searchVisibleProperty().get());
					} );
					toggleSearch.setIconCode(FontAwesomeSolid.SEARCH);
					
					actionsOverviewWindow.titleBar().addAccessories(toggleSearch);

					stg.getIcons().add(new Image(appContext.getIcon().toExternalForm()));
					stg.setMaxWidth(520);
					stg.setMinWidth(520);
					stg.setTitle(RESOURCES.getString("actions"));
					stg.show();
					try {
						if(Boolean.getBoolean("jaja.debugScene"))
							ScenicView.show(stg.getScene());
					}
					catch(Throwable e) {
					}					
				}
				else if(actionsOverviewWindow.stage().isShowing()) {
					actionsOverviewWindow.stage().toFront();
				}
				else  {
					actionsOverviewWindow.stage().show();
				}
			});
		}

		@Override
		public void update(Stage owner) {
			var updateDialogPane = new UpdatePane(this);
			
			var stg = new Stage();
			stg.initOwner(owner);
			stg.initModality(Modality.WINDOW_MODAL);
			var wnd = new JajaFXAppWindow<>(stg, PrettyApp.this, 400, 400);
			wnd.setContent(updateDialogPane);
			wnd.stage().sizeToScene();
			wnd.scene().getRoot().setId("update-dialog");
			stg.getIcons().add(new Image(getIcon().toExternalForm()));
			stg.setResizable(false);
			stg.setTitle(RESOURCES.getString("update"));
			try {
				if(Boolean.getBoolean("jaja.debugScene"))
					ScenicView.show(stg.getScene());
			}
			catch(Throwable e) {
			}
			stg.setOnCloseRequest(eh -> updateDialogPane.hidden());
			updateDialogPane.onRemindMeTomorrow(() -> stg.hide());
			stg.showAndWait();
		}

		@Override
		public Configuration getConfiguration() {
			return configuration;
		}

		@Override
		public UIToolkit<Font, Color> getUiToolkit() {
			return uiToolkit;
		}

		@Override
		public Fonts getFonts() {
			return fonts;
		}

		@Override
		public UpdateService getUpdateService() {
			return PrettyApp.this.getContainer().getUpdateService();
		}

		@Override
		public Preferences getAppPreferences() {
			return getContainer().getRegisteredApp().getAppPreferences();
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
		public URL getIcon() {
			return PrettyApp.this.getIcon();
		}

		@Override
		public boolean isDecorated() {
			return PrettyApp.this.isDecorated();
		}

		@Override
		public Themes getThemes() {
			return themes;
		}

		@Override
		public Path getDefaultWorkingDirectory() {
			return PrettyApp.this.getContainer().getDefaultWorkingDirectory();
		}

		@Override
		public void open(String... args) {
			PrettyApp.this.startupPerformed(String.join(",", Arrays.asList(args).stream().map(s -> "\"" + s + "\"").toList()));
		}
	}
}