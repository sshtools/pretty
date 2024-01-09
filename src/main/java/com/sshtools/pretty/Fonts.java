package com.sshtools.pretty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.terminal.emulation.UIToolkit;
import com.sshtools.terminal.emulation.fonts.FontManager;
import com.sshtools.terminal.emulation.fonts.FontManager.ManagedFont;

import javafx.scene.text.Font;

public class Fonts {

	static Logger LOG = LoggerFactory.getLogger(Fonts.class);

	private final FontManager<Font> fontManager;
	private final Path path;
	private final Map<String, ManagedFont<Font, ?>> userFonts = new HashMap<>();

	public Fonts(AppContext ctx, UIToolkit<Font, ?> uiToolkit) {
		fontManager = new FontManager.Builder<>(uiToolkit).build();
		path = ctx.getConfiguration().dir().resolve("fonts");
		try {
			if (!Files.exists(path)) {
				Files.createDirectories(path);
			}
			ctx.monitor().monitor(path, cb -> {
				var fileChanged = path.resolve(cb.context());
				LOG.info("Watched file changed {}. {}", fileChanged, cb.kind());
				if (cb.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
					if (isFontFile(fileChanged)) {
						uiToolkit.runOnToolkitThread(() -> {
							loadFont(uiToolkit, fileChanged);
							ctx.getConfiguration().put(Constants.FONTS_KEY, getFonts(), Constants.TERMINAL_SECTION);
						});
					}
				} else if (cb.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
					if (isFontFile(fileChanged)) {
						var fnt = userFonts.get(fileChanged.toString());
						if(fnt != null) {
							fontManager.removeFont(fnt);
							LOG.info("User font {} removed.", fileChanged);
							ctx.getConfiguration().put(Constants.FONTS_KEY, getFonts(), Constants.TERMINAL_SECTION);
						}
					}
				}
			});

			for (var fntfile : Files.newDirectoryStream(path, p -> isFontFile(p))) {
				loadFont(uiToolkit, fntfile);
			}

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void setFonts(String[] fonts) {
		var i = 0;
		if(fonts == null || fonts.length == 0 || (/* TODO don't like this */ fonts.length == 1 && fonts[0].equals(""))) {
			fonts = getFonts();
		}
		for(var name : fonts) {
			var fnt = fontManager.getFont(name);
			if(fnt == null)
				LOG.warn("No such font as {}", fonts[i]);
			else
				fontManager.move(fnt, i++);
		}
		
	}
	
	public String[] getFonts() {
		var fnts = fontManager.getFonts(true);
		var nl = fnts.stream().map(mf -> mf.spec().getName()).toList();
		return nl.toArray(new String[0]);
	}

	private boolean isFontFile(Path fileChanged) {
		return fileChanged.getFileName().toString().toLowerCase().endsWith(".ttf") || fileChanged.getFileName().toString().toLowerCase().endsWith(".otf");
	}

	private ManagedFont<Font,?> loadFont(UIToolkit<Font, ?> uiToolkit, Path fileChanged) {
		var fnt = userFonts.get(fileChanged.toString());
		try {
			if (fnt != null) {
				LOG.warn("Font at {} already registered.", fileChanged);
			} else {
				/* TODO: This deprecated toURL() works, but fileChanged.toUri().toString() does not, investigate */
				var tkFont = uiToolkit.loadFont(null, fileChanged.toFile().toURL().toString(), FontManager.DEFAULT_FONT_SIZE);
				fnt = new FontManager.ToolkitFont<Font>(uiToolkit, false, tkFont);
				fontManager.addFont(0, fnt);
				userFonts.put(fileChanged.toString(), fnt);
				LOG.info("User font {} added.", fileChanged);
			}
		} catch (Exception ioe) {
			LOG.error("Failed to load font.", ioe);
		}
		return fnt;
	}

	public Path path() {
		return path;
	}

	public FontManager<Font> getFontManager() {
		return fontManager;
	}
}
