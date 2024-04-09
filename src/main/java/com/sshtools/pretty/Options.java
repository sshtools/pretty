package com.sshtools.pretty;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.function.Predicate;
import java.util.prefs.Preferences;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.sshtools.jajafx.FXUtil;
import com.sshtools.jajafx.JajaFXApp.DarkMode;
import com.sshtools.jajafx.PrefBind;
import com.sshtools.jaul.Phase;
import com.sshtools.pretty.Shells.Shell;
import com.sshtools.pretty.pricli.Styling;
import com.sshtools.terminal.emulation.ResizeStrategy;
import com.sshtools.terminal.emulation.emulator.DECEmulator;
import com.sshtools.terminal.emulation.emulator.XTERM256Color;
import com.sshtools.terminal.emulation.fonts.FontSpec;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;
import uk.co.bithatch.nativeimage.annotations.Bundle;

@Bundle
public class Options extends StackPane implements Closeable {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Options.class.getName());

	@FXML
	private Tab generalTab;
	@FXML
	private ComboBox<FontSpec> fontName;
	@FXML
	private CheckBox automaticUpdates;
	@FXML
	private CheckBox showScrollBar;
	@FXML
	private ComboBox<Phase> phase;
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
	private ListView<TerminalTheme> themes;
	@FXML
	private StackPane preview;
	@FXML
	private HBox customCommandOptions;
	@FXML
	private TextArea customCommand;
	@FXML
	private ToggleGroup passwords;

	private final PrefBind prefBind;
	private final Preferences prefs;

	private final IntegerProperty bufferSizeProperty;
	private final IntegerProperty fontSizeProperty;
	private final ObjectProperty<DarkMode> darkModeproperty;
	private final AppContext app;
	
	public Options(AppContext app) {
		this.app = app;
		
		prefs = app.getPreferences();
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
		defaultShell.setConverter(new StringConverter<Shell>() {
			@Override
			public String toString(Shell object) {
				if(object == null)
					return RESOURCES.getString("customCommand");
				else {
					if(object.version() == null)
						return MessageFormat.format(RESOURCES.getString("shellItemNoVersion"), object.name(), object.id());
					else
						return MessageFormat.format(RESOURCES.getString("shellItem"), object.name(), object.id(), object.version());
				}
			}

			@Override
			public Shell fromString(String string) {
				return null;
			}
		});
		defaultShell.getSelectionModel().selectedItemProperty().addListener((c,o,n) -> {
			if(n != null) {
				cfg.put(Constants.SHELL_KEY,n.id(),  Constants.TERMINAL_SECTION);
			}
		});
		var initialShellName = cfg.get(Constants.SHELL_KEY, Constants.TERMINAL_SECTION);
		var initialShell = app.getShells().getById(initialShellName); 
		if(initialShell.isPresent()) {
			defaultShell.getSelectionModel().select(initialShell.get());
		}
		else {
			customCommand.setText(String.join(System.lineSeparator(), Strings.parseQuotedString(initialShellName)));
		}
		customCommandOptions.managedProperty().bind(customCommandOptions.visibleProperty());
		customCommand.setPromptText(app.getShells().getDefault().map(Shell::toFullCommandText).orElse(RESOURCES.getString("noDefaultShell")));
		customCommandOptions.visibleProperty().bind(Bindings.isNull(defaultShell.getSelectionModel().selectedItemProperty()));
		
		/* Passwords */
		var passwordModeProperty = cfg.getEnumProperty(PasswordMode.class, Constants.PASSWORD_MODE_KEY, Constants.UI_SECTION);
		store.setUserData(PasswordMode.STORE);
		session.setUserData(PasswordMode.SESSION);
		prompt.setUserData(PasswordMode.PROMPT);
		Runnable selectPasswordModeRadios = () -> { 
			switch(passwordModeProperty.get()) {
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
		};
		passwords.selectedToggleProperty().addListener((c,o,n) -> {
			cfg.put(Constants.PASSWORD_MODE_KEY, (PasswordMode)n.getUserData(), Constants.UI_SECTION);
		});
		passwordModeProperty.addListener((c,o,n) -> {
			selectPasswordModeRadios.run();
		});
		selectPasswordModeRadios.run();
		
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
		
		var initialTheme = app.getThemes().getOrDefault(cfg.get(Constants.THEME_KEY, Constants.TERMINAL_SECTION));
		matchThemeToDarkMode.setSelected(!initialTheme.isConcrete());

		var filteredList = new FilteredList<>(app.getThemes().getAll(), 
				createFilter() );
		themes.setItems(filteredList );
		themes.setCellFactory(lv -> new ThemeCell());
		themes.getSelectionModel().select(initialTheme);
		themes.scrollTo(initialTheme);
		themes.getSelectionModel().selectedItemProperty().addListener((c,o,n) -> {
			if(n != null) {
				cfg.put(Constants.THEME_KEY,n.name(),  Constants.TERMINAL_SECTION);
				setupPreviewText(n, panel);
			}
		});
		setupPreviewText(initialTheme, panel);
		matchThemeToDarkMode.selectedProperty().addListener((c, o, n) -> {
			filteredList.setPredicate(createFilter());
		});
		
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
		
		fontName.getItems().addAll(app.getFonts().getFontManager().getFontSpecs());
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
		
		resizeStrategy.getItems().addAll(ResizeStrategy.values());
		resizeStrategy.getSelectionModel().select(ResizeStrategy.SCREEN);
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
		
		showScrollBar.selectedProperty().bind(cfg.getBooleanProperty(Constants.SCROLL_BACK_KEY, Constants.TERMINAL_SECTION));
		
		fontSizeProperty = cfg.getIntProperty("font-size", Constants.TERMINAL_SECTION);
		fontSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(6, 256, fontSizeProperty.get()));
		fontSizeProperty.bind(IntegerProperty.integerProperty(fontSize.getValueFactory().valueProperty()));
		
		bufferSizeProperty = cfg.getIntProperty(Constants.SCROLL_BACK_SIZE_KEY, Constants.TERMINAL_SECTION);
		bufferSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE, bufferSizeProperty.get()));
		bufferSizeProperty.bind(IntegerProperty.integerProperty(bufferSize.getValueFactory().valueProperty()));
		
		darkModeproperty = cfg.getEnumProperty(DarkMode.class, Constants.DARK_MODE_KEY, Constants.UI_SECTION);
		darkMode.getSelectionModel().select(darkModeproperty.get());
		darkModeproperty.bind(darkMode.valueProperty());
		
		screenSize.getItems().addAll("80x24", "132x24", "80x25");
		screenSize.getSelectionModel().select("80x24");

		prefBind = new PrefBind(prefs);
		prefBind.bind(automaticUpdates);
		prefBind.bind(Phase.class, phase);
		

	}
	
	private void setupPreviewText(TerminalTheme theme, JavaFXTerminalPanel panel) {
		var actual = app.getThemes().resolve(theme.name());
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
		fontSizeProperty.unbind();
		bufferSizeProperty.unbind();
		darkModeproperty.unbind();
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
