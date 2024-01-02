package com.sshtools.pretty;

import java.net.URL;
import java.util.prefs.Preferences;

import com.sshtools.jajafx.JajaFXAppWindow;
import com.sshtools.jaul.UpdateService;
import com.sshtools.terminal.emulation.UIToolkit;
import com.sshtools.terminal.emulation.fonts.FontManager;

import javafx.application.HostServices;
import javafx.collections.ObservableList;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public interface AppContext {

	void options(Stage owner);

	TerminalTheme getSelectedTheme();
	
	Themes getThemes();

	void about(Stage owner);

	Configuration getConfiguration();

	UpdateService getUpdateService();

	Preferences getPreferences();

	UIToolkit<Font, Color> getUiToolkit();

	FontManager<Font> getFontManager();

	HostServices getHostServices();

	boolean isDarkMode();

	URL getIcon();

	void addCommonStylesheets(ObservableList<String> stylesheets);

	boolean isDecorated();

	PrettyAppWindow newAppWindow(Stage stage);
}
