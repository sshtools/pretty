package com.sshtools.pretty;

import static com.sshtools.jajafx.FXUtil.maybeQueue;

import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
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
import com.sshtools.jajafx.JajaFXApp;
import com.sshtools.jajafx.JajaFXAppWindow;
import com.sshtools.jaul.UpdateService;
import com.sshtools.terminal.emulation.UIToolkit;
import com.sshtools.terminal.vt.javafx.JavaFXUIToolkit;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Dialog;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Pair;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.Resource;

@Bundle
@Resource({ "themes.properties", "themes/black-on-light-yellow.properties", "themes/black-on-white.properties",
		"themes/green-on-black.properties", "themes/grey-on-black.properties", "themes/solarized-dark.properties",
		"themes/solarized-light.properties", "themes/tango-dark.properties", "themes/tango-light.properties",
		"themes/white-onblack.properties", })
@Reflectable
public class PrettyApp extends JajaFXApp<Pretty, PrettyAppWindow> implements Listener {

	private final static Logger LOG = LoggerFactory.getLogger(PrettyApp.class);
	private final static ResourceBundle RESOURCES = ResourceBundle.getBundle(PrettyApp.class.getName());

	private final JavaFXUIToolkit uiToolkit;
	private final AppContextImpl appContext;
	private final ObjectProperty<DarkMode> darkModeProperty;
	private final Fonts fonts;

	private boolean optionsVisible; 
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
		darkModeProperty = getContainer().getConfiguration().getEnumProperty(DarkMode.class, Constants.DARK_MODE_KEY, Constants.UI_SECTION);
//		new EmojiFonts<Font>(fontManager);
		StartupNotification.registerStartupListener(this);
	}

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void needUpdate() {
		maybeQueue(() -> getWindows().forEach(wnd -> ((PrettyAppWindow) wnd).updateUpdatesState()));
	}

	@Override
	public void addCommonStylesheets(ObservableList<String> stylesheets) {
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
	protected TTYStack createContent(Stage stage) {
		return new TTYStack(appContext, stage);
	}

	@Override
	protected DarkMode getDarkMode() {
		return darkModeProperty.get();
	}

	@Override
	protected JajaFXAppWindow<?> createAppWindow(Stage stage) {
		var ctx = createContent(stage);
		var aw = new PrettyAppWindow(stage, ctx, this, appContext);
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
		cmdline.execute(Strings.parseQuotedString(parameters).toArray(new String[0]));
//		
	}

	@Override
	protected void listenForDarkModeChanges() {
		darkModeProperty.addListener((c,o,n) -> updateDarkMode());
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
		private final Monitor monitor;
		private final SecretStorage passwords;

		AppContextImpl() {
			themes = new Themes(this);
			monitor = new Monitor(this);
			shells = new Shells(monitor, getConfiguration().dir());
			passwords = new DefaultSecretStorage(this);
			getConfiguration().monitor(monitor);
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

			if (optionsVisible)
				return;
			optionsVisible = true;
			Platform.runLater(() -> {
				try {
					var options = new Options(this);

					var dialog = new Dialog<Pair<String, String>>();

					dialog.setHeaderText(RESOURCES.getString("optionsText"));
					dialog.initOwner(owner);
					dialog.initModality(Modality.NONE);

					var fi = new FontIcon();
					fi.setIconCode(FontAwesomeSolid.COGS);
					fi.setIconSize(48);
					dialog.setGraphic(fi);

					var scene = dialog.getDialogPane();
					var window = dialog.getDialogPane().getScene().getWindow();
					dialog.setTitle(" ");
					window.setOnCloseRequest(event -> window.hide());
					PrettyApp.this.updateRootStyles(window.getScene().getRoot());
					
					try {
						if(Boolean.getBoolean("jaja.debugScene"))
							ScenicView.show(scene);
					}
					catch(Throwable e) {
					}

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
			var prefs = getContainer().getConfiguration();
			return themes.resolve(prefs.get(Constants.THEME_KEY, Constants.TERMINAL_SECTION));
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
		public Fonts getFonts() {
			return fonts;
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