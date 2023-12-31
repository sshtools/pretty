package com.sshtools.pretty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

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

	public TerminalTheme get(String name) {
		for (TerminalTheme t : getAll()) {
			if (t.getName().equals(name))
				return t;
		}
		return themes.get(0);
	}

	public ObservableList<TerminalTheme> getAll() {
		return themes;
	}
}
