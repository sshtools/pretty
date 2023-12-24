package com.sshtools.pretty;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import com.sshtools.jajafx.JajaFXApp.DarkMode;
import com.sshtools.jajafx.PrefBind;
import com.sshtools.jaul.Phase;
import com.sshtools.terminal.emulation.ResizeStrategy;
import com.sshtools.terminal.emulation.fonts.FontSpec;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;
import uk.co.bithatch.nativeimage.annotations.Bundle;

@Bundle
public class Options extends StackPane implements Closeable {
	public static final String TERMINAL_SECTION = "terminal";

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
	private ComboBox<TerminalTheme> theme;
	@FXML
	private ComboBox<DarkMode> darkMode;
	@FXML
	private ComboBox<ResizeStrategy> resizeStrategy;
	@FXML
	private Spinner<Integer> fontSize;
	@FXML
	private Spinner<Integer> bufferSize;
	@FXML
	private ComboBox<String> screenSize;

	private final PrefBind prefBind;
	private final Preferences prefs;

	private BooleanProperty scrollBarProperty;

	private IntegerProperty bufferSizeProperty;

	private IntegerProperty fontSizeProperty;
	
	public Options(AppContext app) {
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
		
		theme.getItems().addAll(TerminalTheme.getThemes());
		theme.setConverter(new StringConverter<TerminalTheme>() {
			@Override
			public String toString(TerminalTheme object) {
				return object == null ? "<No theme>" : object.getName();
			}

			@Override
			public TerminalTheme fromString(String name) {
				return TerminalTheme.getTheme(name);
			}
		});
		
		darkMode.getItems().addAll(DarkMode.values());
		darkMode.getSelectionModel().select(DarkMode.AUTO);
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
		
		fontName.getItems().addAll(app.getFontManager().getFontSpecs());
		fontName.setConverter(new StringConverter<FontSpec>() {
			@Override
			public String toString(FontSpec object) {
				return object == null ? RESOURCES.getString("default") : object.getName();
			}

			@Override
			public FontSpec fromString(String name) {
				return app.getFontManager().getFont(name).spec();
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
		
		fontSizeProperty = cfg.getIntProperty("font-size", TERMINAL_SECTION);
		fontSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(6, 256, fontSizeProperty.get()));
		fontSizeProperty.bind(IntegerProperty.integerProperty(fontSize.getValueFactory().valueProperty()));
		
		bufferSizeProperty = cfg.getIntProperty("buffer-size", TERMINAL_SECTION);
		bufferSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE, bufferSizeProperty.get()));
		bufferSizeProperty.bind(IntegerProperty.integerProperty(bufferSize.getValueFactory().valueProperty()));
		
		scrollBarProperty = cfg.getBooleanProperty("scroll-bar", TERMINAL_SECTION);
		scrollBarProperty.bind(showScrollBar.selectedProperty());
		
		screenSize.getItems().addAll("80x24", "132x24", "80x25");
		screenSize.getSelectionModel().select("80x24");
//		screenSize.disableProperty().bind(Bindings.notEqual(ResizeStrategy.FONT, resizeStrategy.valueProperty()));

		prefBind = new PrefBind(prefs);
		prefBind.bind(automaticUpdates);
//		prefBind.bind(Phase.class, phase);
//		prefBind.bind(DarkMode.class, darkMode);
//		prefBind.bind(ResizeStrategy.class, resizeStrategy);
//		prefBind.bind(theme);
//		prefBind.bind(fontName);
//		prefBind.bind(screenSize);

	}

	@Override
	public void close() {
		fontSizeProperty.unbind();
		bufferSizeProperty.unbind();
		scrollBarProperty.unbind();
		prefBind.close();
	}

}
