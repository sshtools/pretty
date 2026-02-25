package com.sshtools.pretty;

import static com.sshtools.jajafx.FXUtil.maybeQueue;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Predicate;
import java.util.prefs.Preferences;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pty4j.Platform;
import com.sshtools.jajafx.FXUtil;
import com.sshtools.jajafx.JajaFXApp.DarkMode;
import com.sshtools.jajafx.PrefBind;
import com.sshtools.jaul.Phase;
import com.sshtools.jini.Data.Handle;
import com.sshtools.pretty.Fonts.FontsChangeListener;
import com.sshtools.pretty.Shells.Shell;
import com.sshtools.pretty.pricli.Styling;
import com.sshtools.terminal.emulation.ResizeStrategy;
import com.sshtools.terminal.emulation.emulator.DECEmulator;
import com.sshtools.terminal.emulation.emulator.XTERM256Color;
import com.sshtools.terminal.emulation.fonts.FontSpec;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

import javafx.beans.binding.Bindings;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import uk.co.bithatch.nativeimage.annotations.Bundle;

@Bundle
public class Options extends StackPane implements Closeable {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Options.class.getName());
	
	private final static Logger  LOG = LoggerFactory.getLogger(Options.class);

	@FXML
	private Tab generalTab;
	@FXML
	private ComboBox<FontSpec> fontName;
	@FXML
	private CheckBox automaticUpdates;
	@FXML
	private CheckBox limitBuffer;
	@FXML
	private ComboBox<Phase> phase;
	@FXML
	private CheckBox loginShell;
	@FXML
	private CheckBox console;
	@FXML
	private CheckBox legacyPty;
	@FXML
	private CheckBox matchThemeToDarkMode;
	@FXML
	private ComboBox<DarkMode> darkMode;
	@FXML
	private ComboBox<ResizeStrategy> resizeStrategy;
	@FXML
	private ComboBox<Shell> defaultShell;
	@FXML
	private Spinner<Integer> fontSize;
	@FXML
	private Spinner<Integer> bufferSize;
	@FXML
	private ComboBox<String> screenSize;
	@FXML
	private RadioButton store;
	@FXML
	private RadioButton session;
	@FXML
	private RadioButton prompt;
	@FXML
	private RadioButton automaticScrollBar;
	@FXML
	private RadioButton permanentScrollBar;
	@FXML
	private RadioButton hideScrollBar;
	@FXML
	private Slider opacity;
	@FXML
	private ListView<TerminalTheme> themes;
	@FXML
	private StackPane preview;
	@FXML
	private HBox customCommandOptions;
	@FXML
	private Hyperlink checkForUpdates;
	@FXML
	private TextArea customCommand;
	@FXML
	private ToggleGroup passwords;
	@FXML
	private ToggleGroup scrollBarMode;
	@FXML
	private VBox scrollBarModes;
	@FXML
	private ColorPicker accentColor;
	@FXML
	private FontIcon updateSpinner;
	@FXML
	private CheckBox statusLine;
	@FXML
	private CheckBox fileTransferNotifications;;
	@FXML
	private TextField downloads;

	private final PrefBind prefBind;
	private final Preferences prefs;

	private final AppContext app;
	private final List<Handle> handles = new ArrayList<>();
	private final FontsChangeListener listener;
	
	public Options(AppContext app) {
		this.app = app;
		
		prefs = app.getAppPreferences();
		var cfg = app.getConfiguration();
		
		var loader = new FXMLLoader(getClass().getResource("Options.fxml"));
		loader.setController(this);
		loader.setRoot(this);
		loader.setResources(RESOURCES);
		try {
			loader.load();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		

		FXUtil.addIfNotAdded(getStylesheets(), Options.class.getResource("Options.css").toExternalForm());
		
		/* Update phases */
		phase.getItems().addAll(app.getUpdateService().getPhases());
		phase.setConverter(new StringConverter<Phase>() {
			@Override
			public String toString(Phase object) {
				return object == null ? null : RESOURCES.getString("phase." + object.name());
			}

			@Override
			public Phase fromString(String string) {
				return null;
			}
		});
		
		/* Shells */
		defaultShell.getItems().setAll(app.getShells().getAll());
		defaultShell.getItems().add(null);
		defaultShell.setConverter(Shells.stringConverter());
		defaultShell.getSelectionModel().selectedItemProperty().addListener((c,o,n) -> {
			if(n != null) {
				cfg.terminal().put(Constants.SHELL_KEY,n.id());
			}
			updateState();
		});
		var initialShellName = cfg.terminal().get(Constants.SHELL_KEY);
		var initialShell = app.getShells().getById(initialShellName); 
		if(initialShell.isPresent()) {
			defaultShell.getSelectionModel().select(initialShell.get());
		}
		else {
			customCommand.setText(String.join(System.lineSeparator(), Strings.parseQuotedString(initialShellName)));
		}
		customCommandOptions.managedProperty().bind(customCommandOptions.visibleProperty());
		customCommand.setPromptText(app.getShells().getDefault().map((shl) -> shl.toFullCommandText(false)).orElse(RESOURCES.getString("noDefaultShell")));
		customCommandOptions.visibleProperty().bind(Bindings.isNull(defaultShell.getSelectionModel().selectedItemProperty()));
		
		/* Passwords */
		store.setUserData(PasswordMode.STORE);
		session.setUserData(PasswordMode.SESSION);
		prompt.setUserData(PasswordMode.PROMPT);
		cfg.bindEnum(PasswordMode.class, (mode) -> { 
			switch(mode) {
			case STORE:
				passwords.selectToggle(store);
				break;
			case SESSION:
				passwords.selectToggle(session);
				break;
			default:
				passwords.selectToggle(prompt);
				break;
			}
		}, () -> {
			return PasswordMode.PROMPT;
		}, Constants.PASSWORD_MODE_KEY, Constants.TERMINAL_SECTION);
		passwords.selectedToggleProperty().addListener((c,o,n) -> {
			cfg.ui().putEnum(Constants.PASSWORD_MODE_KEY, (PasswordMode)n.getUserData());
		});
		
		/* Themes and Preview terminal */
		var emulator = new DECEmulator<JavaFXTerminalPanel>(XTERM256Color.ID, 33, 14);
		var panel = new JavaFXTerminalPanel.Builder().
				withUiToolkit(app.getUiToolkit()).
				withFontManager(app.getFonts().getFontManager()).
				withBuffer(emulator)
				.build();
		emulator.getModes().setCursorBlink(true);
		panel.setResizeStrategy(ResizeStrategy.SCREEN);
		preview.getChildren().add(panel.getControl());
		
		var initialTheme = app.getThemes().getOrDefault(cfg.terminal().get(Constants.THEME_KEY));
		matchThemeToDarkMode.setSelected(!initialTheme.isConcrete());

		var filteredList = new FilteredList<>(app.getThemes().getAll(), 
				createFilter() );
		themes.setItems(filteredList );
		themes.setCellFactory(lv -> new ThemeCell());
		handles.add(cfg.bindString(t -> themes.getSelectionModel().select(app.getThemes().getOrDefault(cfg.terminal().get(Constants.THEME_KEY))), 
				() -> themes.getSelectionModel().getSelectedItem().id(), Constants.THEME_KEY, Constants.TERMINAL_SECTION));
		
		themes.getSelectionModel().select(initialTheme);
		themes.scrollTo(initialTheme);
		themes.getSelectionModel().selectedItemProperty().addListener((c,o,n) -> {
			if(n != null) {
				cfg.terminal().put(Constants.THEME_KEY,n.id());
				setupPreviewText(n, panel);
			}
		});
		setupPreviewText(initialTheme, panel);
		matchThemeToDarkMode.selectedProperty().addListener((c, o, n) -> {
			filteredList.setPredicate(createFilter());
		});

		/* Opacity */
		handles.add(cfg.bindInteger(val -> opacity.setValue(val), () -> (int)opacity.getValue(), Constants.OPACITY_KEY, Constants.TERMINAL_SECTION));
		opacity.valueProperty().addListener((c,o,n) -> cfg.terminal().put(Constants.OPACITY_KEY, n.intValue()));
		
		/* Dark Mode */
		darkMode.getItems().addAll(DarkMode.values());
		darkMode.setConverter(new StringConverter<DarkMode>() {
			@Override
			public String toString(DarkMode object) {
				return RESOURCES.getString("darkMode." + object.name());
			}

			@Override
			public DarkMode fromString(String name) {
				for(var mode : DarkMode.values()) {
					if(toString(mode).equals(name)) {
						return mode;
					}
				}
				return null;
			}
		});
		handles.add(cfg.bind(DarkMode.class, darkMode.getSelectionModel(), Constants.DARK_MODE_KEY, Constants.UI_SECTION));
		
		/* Accent color */
		accentColor.valueProperty().addListener((c,o,n) -> cfg.ui().put(Constants.ACCENT_COLOR_KEY, Colors.toHex(n)));
		handles.add(cfg.bindString((cstr) -> accentColor.setValue(Colors.parse(cstr, Color.valueOf("#0078d7"))), () -> Colors.toHex(accentColor.getValue()) , Constants.ACCENT_COLOR_KEY, Constants.UI_SECTION));

		/* Font */
		fontName.getItems().setAll(app.getFonts().getFontManager().getFontSpecs());
		fontName.setConverter(new StringConverter<FontSpec>() {
			@Override
			public String toString(FontSpec object) {
				return object == null ? RESOURCES.getString("default") : object.getName();
			}

			@Override
			public FontSpec fromString(String name) {
				return app.getFonts().getFontManager().getFont(name).spec();
			}
		});
		listener = () -> maybeQueue(() -> fontName.getItems().setAll(app.getFonts().getFontManager().getFontSpecs()));
		fontName.getSelectionModel().selectedItemProperty().addListener((c,o,n) -> {
			var cl = new ArrayList<>(Arrays.asList(cfg.terminal().getAllElse(Constants.FONTS_KEY)));
			if(n != null) {
				cl.remove(n.getName());
				cl.add(0, n.getName());
				cfg.terminal().putAll(Constants.FONTS_KEY, cl.toArray(new String[0]));
			}
		});
		app.getFonts().addListener(listener);
		  
//		
		
		handles.add(cfg.bindString(fn -> { 
			var fnt = app.getFonts().getFontManager().getFont(fn);
			if(fnt == null)
				fontName.getSelectionModel().select(0);
			else
				fontName.getSelectionModel().select(fnt.spec()); 
		}, () -> fontName.getSelectionModel().getSelectedItem().getName(), Constants.FONTS_KEY, Constants.TERMINAL_SECTION));
		fontSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(6, 256, 6));
		handles.add(cfg.bind(fontSize.getValueFactory().valueProperty(), Constants.FONT_SIZE_KEY, Constants.TERMINAL_SECTION));

		/* Sizing */
		resizeStrategy.getItems().addAll(ResizeStrategy.values());
		handles.add(cfg.bind(ResizeStrategy.class, resizeStrategy.getSelectionModel(), Constants.RESIZE_STRATEGY_KEY, Constants.TERMINAL_SECTION));
		resizeStrategy.setConverter(new StringConverter<ResizeStrategy>() {
			@Override
			public String toString(ResizeStrategy object) {
				return RESOURCES.getString("resizeStrategy." + object.name());
			}

			@Override
			public ResizeStrategy fromString(String name) {
				for(var mode : ResizeStrategy.values()) {
					if(toString(mode).equals(name)) {
						return mode;
					}
				}
				return null;
			}
		});		
		
		screenSize.getItems().addAll("80x24", "132x24", "80x25");
		handles.add(cfg.bindString(sz -> screenSize.setValue(sz), () -> screenSize.getValue(), Constants.SCREEN_SIZE_KEY, Constants.TERMINAL_SECTION));
		screenSize.valueProperty().addListener((c,o,n) -> cfg.terminal().put(Constants.SCREEN_SIZE_KEY, n));
		screenSize.getSelectionModel().select("80x24");
		
		/* Status Line */
		handles.add(cfg.bind(statusLine.selectedProperty(), Constants.ENABLED_KEY, Constants.STATUS_SECTION));

		/* Scrolling */
		automaticScrollBar.setUserData(ScrollBarMode.AUTOMATIC);
		permanentScrollBar.setUserData(ScrollBarMode.PERMANENT);
		hideScrollBar.setUserData(ScrollBarMode.HIDDEN);
		cfg.bindEnum(ScrollBarMode.class, (mode) -> { 
			switch(mode) {
			case AUTOMATIC:
				scrollBarMode.selectToggle(automaticScrollBar);
				break;
			case PERMANENT:
				scrollBarMode.selectToggle(permanentScrollBar);
				break;
			default:
				scrollBarMode.selectToggle(hideScrollBar);
				break;
			}
		}, () -> {
			return (ScrollBarMode)scrollBarMode.getSelectedToggle().getUserData();
		}, Constants.SCROLL_BAR_KEY, Constants.TERMINAL_SECTION);
		scrollBarMode.selectedToggleProperty().addListener((c,o,n) -> {
			cfg.terminal().putEnum(Constants.SCROLL_BAR_KEY, (ScrollBarMode)n.getUserData());
		});
		scrollBarModes.visibleProperty().bind(Bindings.or(Bindings.not(limitBuffer.selectedProperty()),  Bindings.notEqual(0, bufferSize.valueProperty())));
		handles.add(cfg.bind(limitBuffer.selectedProperty(), Constants.LIMIT_BUFFER_KEY, Constants.TERMINAL_SECTION));
		bufferSize.disableProperty().bind(Bindings.not(limitBuffer.selectedProperty()));
		
		bufferSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE, 0, 1000));
		handles.add(cfg.bind(bufferSize.getValueFactory().valueProperty(), Constants.BUFFER_SIZE_KEY, Constants.TERMINAL_SECTION));

		/* Shell */
		handles.add(cfg.bind(loginShell.selectedProperty(), Constants.LOGIN_SHELL_KEY, Constants.TERMINAL_SECTION));		
		loginShell.managedProperty().bind(loginShell.visibleProperty());

		handles.add(cfg.bind(console.selectedProperty(), Constants.CONSOLE_KEY, Constants.TERMINAL_SECTION));
		console.managedProperty().bind(console.visibleProperty());

		handles.add(cfg.bind(legacyPty.selectedProperty(), Constants.LEGACY_PTY_KEY, Constants.TERMINAL_SECTION));		
		legacyPty.managedProperty().bind(legacyPty.visibleProperty());
		legacyPty.selectedProperty().addListener((c,o,n) -> updateState());
		
		/* File transfers */
		handles.add(cfg.bind(fileTransferNotifications.selectedProperty(), Constants.NOTIFICATIONS_KEY, Constants.TRANSFERS_SECTION));
		handles.add(cfg.bind(downloads.textProperty(), Constants.DOWNLOADS_KEY, Constants.TRANSFERS_SECTION));
		
		/* Java Preferences stuff, i.e. Jaul updates */
		prefBind = new PrefBind(prefs);
		prefBind.bind(automaticUpdates);
		prefBind.bind(Phase.class, phase);
		
		updateSpinner.setVisible(false);

		updateState();

	}
	
	@FXML
	public void checkForUpdates(ActionEvent evt) {
		var usrv = app.getUpdateService();
		checkForUpdates.setDisable(true);
		updateSpinner.setVisible(true);
		FXUtil.spin(updateSpinner, true);
		usrv.getContext().getScheduler().execute(() -> {
			try {
				usrv.checkForUpdate();
			} catch (IOException e) {
				LOG.error("Failed to check for updates", e);
			} finally {
				maybeQueue(() -> { 
					checkForUpdates.setDisable(false);
					updateSpinner.setVisible(false);
					FXUtil.spin(updateSpinner, false);
					updateState();
					if(usrv.isNeedsUpdating()) {
						app.update((Stage) getParent().getScene().getWindow());
					}
				});
			}
		});
	}
	
	@FXML
	public void dropInShells(ActionEvent evt) {
		app.getHostServices().showDocument(app.getShells().getDropInPath().toUri().toString());
	}
	
	@FXML
	public void dropInFonts(ActionEvent evt) {
		app.getHostServices().showDocument(app.getFonts().getDropInPath().toUri().toString());
	}
	
	@FXML
	public void dropInThemes(ActionEvent evt) {
		app.getHostServices().showDocument(app.getThemes().getDropInPath().toUri().toString());
	}
	
	@FXML
	public void useCurrentSize(ActionEvent evt) {
		var owner = ((Stage)getScene().getWindow()).getOwner();
		app.getWindows().forEach(wnw -> {
			if(wnw.stage().equals(owner)) {
				var pw = (PrettyAppWindow)wnw;
				pw.ttyContext().activeTty().ifPresent(tty -> {
					var page = tty.terminal().getViewport().getPage();
					screenSize.setValue(page.columns() + "x" + page.height());
				});
			}
		});
	}
	
	@FXML
	public void browseDownloads(ActionEvent evt) {
		var chooser = new DirectoryChooser();
		chooser.setTitle(RESOURCES.getString("downloads"));
		var rawPath = downloads.getText();
		if(rawPath != null && !rawPath.isBlank()) {
			var expanded = rawPath.startsWith("~") ? System.getProperty("user.home") + rawPath.substring(1) : rawPath;
			var initial = new File(expanded);
			if(initial.exists() && initial.isDirectory()) {
				chooser.setInitialDirectory(initial);
			}
		}
		var window = getScene() == null ? null : getScene().getWindow();
		var selected = chooser.showDialog(window);
		if(selected != null) {
			var home = System.getProperty("user.home");
			var selectedPath = selected.toPath().toAbsolutePath().normalize();
			var display = selectedPath.toString();
			if(home != null && !home.isBlank()) {
				var homePath = Paths.get(home).toAbsolutePath().normalize();
				if(selectedPath.startsWith(homePath)) {
					var suffix = homePath.relativize(selectedPath).toString();
					display = suffix.isEmpty() ? "~" : "~" + File.separator + suffix;
				}
			}
			downloads.setText(display);
		}
	}
	
	private void updateState() {
		var sel = defaultShell.getSelectionModel().getSelectedItem();
		loginShell.setVisible(sel != null && sel.loginShellArgs() != null && sel.loginShellArgs().length > 0);
		legacyPty.setVisible(System.getProperty("os.name").toLowerCase().contains("windows"));
		console.setVisible(!Platform.isWindows() || (Platform.isWindows() && legacyPty.isSelected()));
	}
	
	private void setupPreviewText(TerminalTheme theme, JavaFXTerminalPanel panel) {
		var actual = app.getThemes().resolve(theme.id());
		actual.apply(panel, 100); 
		var emu = panel.getViewport();
		emu.clearScreen();
		try {
			emu.writeString(Styling.styled(MessageFormat.format(RESOURCES.getString("lorem"), actual.name(), actual.description())).toAnsi());
			emu.newline();
			emu.cr();
			emu.newline();
			var bldr = new AttributedStringBuilder();
			for(int i = 0 ; i < 16; i++) {
				bldr.style(new AttributedStyle().background(i));
				bldr.append("  ");
				bldr.style(new AttributedStyle().backgroundOff());
			}
			emu.writeString(bldr.toAnsi());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private Predicate<? super TerminalTheme> createFilter() {
		return t ->  matchThemeToDarkMode.isSelected() ? 
				( t.isAbstract() || t.isStatic() ) :
				( t.isConcrete() || t.isStatic() );
	}

	@Override
	public void close() {
		app.getFonts().removeListener(listener);
		handles.forEach(Handle::close);
		handles.clear();
		prefBind.close();
	}

	final class ThemeCell extends ListCell<TerminalTheme> {

		private final ThemeCellController ccc = new ThemeCellController();
		private final Node view = ccc.getView();

		@Override
		protected void updateItem(TerminalTheme item, boolean empty) {
			super.updateItem(item, empty);
			if (empty) {
				setGraphic(null);
			} else {
				ccc.setItem(item);
				setGraphic(view);
			}
		}
	}
	
	final class ThemeCellController {
		private BorderPane view = new BorderPane();
		private Label label = new Label();
		private Label info = new Label();

		{
			info.setAlignment(Pos.CENTER_LEFT);
			label.setAlignment(Pos.CENTER_LEFT);
			view.setBackground(null);
			label.getStyleClass().add("strong");
			view.getStyleClass().add("theme-cell");
			view.setTop(label);
			view.setBottom(info);
		}

		public void setItem(TerminalTheme item) {
			label.setText(item.name());
			info.setText(item.description());
		}

		public Node getView() {
			return view;
		}
	}
}
