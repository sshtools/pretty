package com.sshtools.pretty;

import static com.sshtools.terminal.emulation.VDUColor.fromString;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.INI;
import com.sshtools.terminal.emulation.Colors;
import com.sshtools.terminal.emulation.Colors.Size;
import com.sshtools.terminal.emulation.VDUColor;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

public class TerminalTheme {
	private static final String DEFAULT_THEME = "LogonBox Blue";
	public static Logger LOG = LoggerFactory.getLogger(TerminalTheme.class);

	final static String BACKGROUND_COLOR = "bg";
	final static String FOREGROUND_COLOR = "fg";
	final static String COLOR_PRINTING = "color-printing";
	final static String CURSOR_FOREGROUND = "cursor-fg";
	final static String CURSOR_BACKGROUND = "cursor-bg";
	final static String SELECTION_FOREGROUND = "selection-fg";
	final static String SELECTION_BACKGROUND = "selection-bg";
	final static String LSCOLORS = "LSCOLORS";
	final static String LS_COLORS = "LS_COLORS";
	
	private String name;
	private String path;
	private INI properties;
	private static List<TerminalTheme> themes;
	
	public final static TerminalTheme CUSTOM = new TerminalTheme("Custom", "");

	TerminalTheme(String name, String path) {
		this.name = name;
		this.path = path;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return name;
	}

	public INI ini() {
		if (properties == null) {
			try (InputStream in = TerminalTheme.class.getResourceAsStream(path)) {
				if(in == null)
					throw new FileNotFoundException(String.format("No such theme resource %s for theme %s", path, name));
				properties = INI.fromInput(in).readOnly();
			} catch (IOException ioe) {
				throw new RuntimeException("Failed to load themes.", ioe);
			}
		}
		return properties;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TerminalTheme other = (TerminalTheme) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	public static List<TerminalTheme> getThemes() {
		if (themes == null) {
			themes = new ArrayList<>();
			try (BufferedReader br = new BufferedReader(
					new InputStreamReader(TerminalTheme.class.getResourceAsStream("/themes.properties")))) {
				String line = null;
				while ((line = br.readLine()) != null) {
					line = line.trim();
					if (!line.startsWith("#")) {
						int idx = line.indexOf('=');
						if (idx != -1) {
							themes.add(new TerminalTheme(line.substring(0, idx), line.substring(idx + 1)));
						}
					}
				}
			} catch (IOException ioe) {
				throw new RuntimeException("Failed to load themes.", ioe);
			}
		}
		return themes;
	}

	public static TerminalTheme getTheme(String name) {
		if(name == null || name.equals("") || name.equals(CUSTOM.getName()))
			return CUSTOM;
		for (TerminalTheme t : getThemes()) {
			if (t.getName().equals(name))
				return t;
		}
		return CUSTOM;
	}

	public boolean isCustom() {
		return this.equals(CUSTOM);
	}

	public static TerminalTheme getDefault() {
		var t = getTheme(DEFAULT_THEME);
		return t == null ? getThemes().get(0) : t;
	}

	public void apply(JavaFXTerminalPanel terminalPanel) {
		synchronized(terminalPanel.getViewport().getBufferLock()) {
			terminalPanel.setDefaultBackground(background());
			terminalPanel.setDefaultForeground(foreground());
			terminalPanel.setSelectionBackground(selectionBackground());
			terminalPanel.setSelectionForeground(selectionForeground());
			terminalPanel.setCursorColors(cursorForeground(), cursorBackground());
			terminalPanel.getViewport().getColors().setPalette(Size.PAL16, pal16().orElse(Colors.PAL16_DEFAULT.getColors()));
			terminalPanel.getViewport().getColors().setPalette(Size.PAL256, pal256().orElse(Colors.PAL256_DEFAULT.getColors()));
			terminalPanel.getViewport().redisplay();
		}
	}
	
	public Optional<VDUColor[]> pal16() {
		var tprops = ini();
		var secOr = tprops.sectionOr("palette");
		if(secOr.isPresent()) {
			var keys = secOr.get();
			if(keys.keys().size() == 16 || keys.keys().size() == 8) {
				var colors = keys.keys().stream().map(k -> fromString(keys.get(k))).toList().toArray(new VDUColor[0]);
				if(colors.length == 8) {
					var allColors =new VDUColor[16];
					System.arraycopy(colors, 0, allColors, 0, 8);
					for(int i = 0 ; i < 8 ; i++) {
						allColors[i + 8] = allColors[i].brighter();
					}
					colors = allColors;
				}
				return Optional.of(colors);
			}
			else {
				LOG.warn("If the 'palette' section is present, it must have either 8 or 16 entries.");
			}
		}
		return Optional.empty();
		
	}
	
	public Optional<VDUColor[]> pal256() {
		var tprops = ini();
		var secOr = tprops.sectionOr("palette-256");
		if(secOr.isPresent()) {
			var keys = secOr.get();
			if(keys.keys().size() == 256) {
				keys.keys().stream().map(k -> fromString(keys.get(k))).toList().toArray(new VDUColor[0]);
			}
			else {
				LOG.warn("If the 'palette-256' section is present, it must have exactly 256 entries.");
			}
		}
		return Optional.empty();
		
	}

	public VDUColor cursorForeground() {
		var tprops = ini();
		return tprops.getOr(CURSOR_FOREGROUND).map(VDUColor::fromString).
			orElseGet(this::background);
	}

	public VDUColor cursorBackground() {
		var tprops = ini();
		return tprops.getOr(CURSOR_BACKGROUND).map(VDUColor::fromString).
			orElseGet(this::foreground);
	}

	public VDUColor selectionForeground() {
		var tprops = ini();
		return tprops.getOr(SELECTION_FOREGROUND).map(VDUColor::fromString).
		orElseGet(this::background);
	}

	public VDUColor selectionBackground() {
		var tprops = ini();
		return tprops.getOr(SELECTION_BACKGROUND).map(VDUColor::fromString).
		orElseGet(this::foreground);
	}

	public VDUColor foreground() {
		var tprops = ini();
		return tprops.getOr(FOREGROUND_COLOR).map(VDUColor::fromString).
		orElseGet(() -> VDUColor.BLACK);
	}

	public VDUColor background() {
		var tprops = ini();
		return tprops.getOr(BACKGROUND_COLOR).map(VDUColor::fromString).
		orElseGet(() -> VDUColor.BLACK);
	}
}
