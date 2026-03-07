package com.sshtools.pretty;

import static com.sshtools.jajafx.FXUtil.maybeQueue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.INI;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.config.INISet;
import com.sshtools.jini.config.Monitor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Themes extends AbstractINISetSystem {
	private final static Logger LOG = LoggerFactory.getLogger(Themes.class);

	private final Map<String, TerminalTheme> themes = new LinkedHashMap<>();
	private final ObservableList<TerminalTheme> themeList = FXCollections.observableArrayList();

	private final INI ini;

	public Themes(AppContext app, Monitor monitor) {
		super(app, "themes.d");
		try {
			Files.createDirectories(getDropInPath());
		} catch (IOException e) {
			LOG.warn("Failed to create drop-in themes folder.", e);
		}
		ini = new INISet.Builder("themes").
				withMonitor(monitor).
				withDefault(Themes.class, "Themes.ini").
				build().
				document();
		loadThemesFromIni();
	}
	
	public INI document() {
		return ini;
	}

	public TerminalTheme resolve(String id) {
		var theme = get(id);
		if (theme.isPresent()) {
			if (theme.get().isAbstract()) {
				if (app.isDarkMode()) {
					var dm = theme.get().darkModeTheme().map(this::get).orElse(Optional.of(getDefault()));
					if (dm.isPresent()) {
						return dm.get();
					}
				} else {
					var lm = theme.get().lightModeTheme().map(this::get).orElse(Optional.of(getDefault()));
					if (lm.isPresent()) {
						return lm.get();
					}
				}
			} else
				return theme.get();
		}
		return getDefault();
	}

	private TerminalTheme getDefault() {
		return themeList.get(0);
	}

	public Optional<TerminalTheme> get(String id) {
		for (TerminalTheme t : getAll()) {
			if (t.id().equals(id))
				return Optional.of(t);
		}
		return Optional.empty();
	}

	public ObservableList<TerminalTheme> getAll() {
		return themeList;
	}

	public TerminalTheme getOrDefault(String name) {
		return get(name).orElseGet(this::getDefault);
	}

	private void loadThemesFromIni() {
		Arrays.asList(ini.allSections()).forEach(sec -> {
			try {
				addOrUpdateTheme(new TerminalTheme(app, sec));
			} catch (Exception e) {
				LOG.debug("Failed to load theme.", e);
			}
		});
		ini.onValueUpdate(vu -> {
			if (vu.parent() instanceof Section sec) {
				while(sec.path().length > 1)
					sec = sec.parent();
				var thm = new TerminalTheme(app, sec);
				maybeQueue(() -> addOrUpdateTheme(thm));
			}
		});
		ini.onSectionUpdate(su -> {
			var sec = su.section();
			while(sec.path().length > 1)
				sec = sec.parent();
			switch (su.type()) {
			case REMOVE:
			{
				var thm = themes.remove(sec.key());
				if (thm != null) {
					LOG.info("Removing theme '{}' [{}]", thm.id(), thm.name());
					maybeQueue(() -> themeList.remove(thm));
				}
				break;
			}
			case ADD:
			{
				var thm = new TerminalTheme(app, sec);
				maybeQueue(() -> addOrUpdateTheme(thm));
				break;
			}
			default:
				/* Ignore section updates, we may get partial sections */
				/* TODO fix this in jini? */
				break;
			}
		});

	}

	private void addOrUpdateTheme(TerminalTheme theme) {
		var was = themes.put(theme.id(), theme);
		if (was != null) {
			themeList.remove(was);
			LOG.info("Updating theme '{}' [{}]", theme.id(), theme.name());
		} else {
			LOG.info("Adding theme '{}' [{}]", theme.id(), theme.name());
		}
		themeList.add(theme);
	}
}
