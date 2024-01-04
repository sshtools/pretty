package com.sshtools.pretty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Optional;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Themes {

	private final ObservableList<TerminalTheme> themes = FXCollections.observableArrayList();
	
	private AppContext app;
	
	public Themes(AppContext app) {
		this.app = app;
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(TerminalTheme.class.getResourceAsStream("/themes.properties")))) {
			String line = null;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (!line.startsWith("#")) {
					int idx = line.indexOf('=');
					if (idx != -1) {
						themes.add(new TerminalTheme(app, line.substring(0, idx), line.substring(idx + 1)));
					}
				}
			}
		} catch (IOException ioe) {
			throw new RuntimeException("Failed to load themes.", ioe);
		}
	}

	public TerminalTheme resolve(String name) {
		var theme = get(name);
		if(theme.isPresent()) {
			if(theme.get().isAbstract()) {
				if(app.isDarkMode()) {
					var dm = theme.get().darkModeTheme().map(this::get).orElse(Optional.of(getDefault()));
					if(dm.isPresent()) {
						return dm.get();
					}
				}
				else  {
					var lm = theme.get().lightModeTheme().map(this::get).orElse(Optional.of(getDefault()));
					if(lm.isPresent()) {
						return lm.get();
					}
				}
			}
			else
				return theme.get();
		}
		return getDefault();
	}

	private TerminalTheme getDefault() {
		return themes.get(0);
	}

	public Optional<TerminalTheme> get(String name) {
		for (TerminalTheme t : getAll()) {
			if (t.name().equals(name))
				return Optional.of(t);
		}
		return Optional.empty();
	}

	public ObservableList<TerminalTheme> getAll() {
		return themes;
	}

	public TerminalTheme getOrDefault(String name) {
		return get(name).orElseGet(this::getDefault);
	}
}
