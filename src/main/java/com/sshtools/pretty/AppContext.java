package com.sshtools.pretty;

import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.prefs.Preferences;

import com.sshtools.jaul.UpdateService;
import com.sshtools.terminal.emulation.UIToolkit;

import javafx.application.HostServices;
import javafx.collections.ObservableList;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public interface AppContext {
	
	SecretStorage passwords();

	Monitor monitor();
	
	void options(Stage owner);

	TerminalTheme getSelectedTheme();
	
	Themes getThemes();

	void about(Stage owner);

	Configuration getConfiguration();

	UpdateService getUpdateService();

	Preferences getPreferences();

	UIToolkit<Font, Color> getUiToolkit();

	Fonts getFonts();

	HostServices getHostServices();

	boolean isDarkMode();

	URL getIcon();

	void addCommonStylesheets(ObservableList<String> stylesheets);

	boolean isDecorated();

	default PrettyAppWindow newAppWindow(Stage stage) {
		return newAppWindow(stage, Optional.of(new TTYRequest.Builder().build()));
	}

	PrettyAppWindow newAppWindow(Stage stage, Optional<TTYRequest> request);

	Path getDefaultWorkingDirectory();
	
	Shells getShells();

	void open(String... args);

	ScheduledExecutorService scheduler();
}
