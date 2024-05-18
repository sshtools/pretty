package com.sshtools.pretty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.terminal.emulation.UIToolkit;
import com.sshtools.terminal.emulation.fonts.FontManager;
import com.sshtools.terminal.emulation.fonts.FontManager.ManagedFont;
//import com.sshtools.terminal.fonts.TrueTypeFonts;

import javafx.scene.text.Font;

public class Fonts extends AbstractINISetSystem  { 
	
	@FunctionalInterface	
	public interface FontsChangeListener {
		void changed();
	}

	static Logger LOG = LoggerFactory.getLogger(Fonts.class);

	private final FontManager<Font> fontManager;
	private final Map<String, ManagedFont<Font, ?>> userFonts = new HashMap<>();
//	private final TrueTypeFonts<Font> trueTypeFonts;
	private final Path primaryFontsPath;
	private final List<FontsChangeListener> listeners = new ArrayList<>(); 

	public Fonts(AppContext ctx, UIToolkit<Font, ?> uiToolkit) {
		super(ctx, "fonts.d");
		fontManager = new FontManager.Builder<>(uiToolkit).
				build();
		
		new EmojiSupport(fontManager);
		
//		trueTypeFonts = new TrueTypeFonts<>(fontManager, uiToolkit);
		
		primaryFontsPath = ctx.getConfiguration().dir().resolve("fonts.d");
		var supplementalFontsPath = primaryFontsPath.resolve("supplemental");
		try {
			maybeMkdir(primaryFontsPath, supplementalFontsPath);
			
			loadAndMonitorDir(ctx, uiToolkit, primaryFontsPath, false);
			loadAndMonitorDir(ctx, uiToolkit, supplementalFontsPath, true);

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	public void addListener(FontsChangeListener listener) {
		listeners.add(listener);
	}
	
	public void removeListener(FontsChangeListener listener) {
		listeners.remove(listener);
	}

	public FontManager<Font> getFontManager() {
		return fontManager;
	}

	public String[] getFonts() {
		var fnts = fontManager.getFonts(true);
		var nl = fnts.stream().map(mf -> mf.spec().getName()).toList();
		return nl.toArray(new String[0]);
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
	
	private boolean isFontFile(Path fileChanged) {
		return fileChanged.getFileName().toString().toLowerCase().endsWith(".ttf") || fileChanged.getFileName().toString().toLowerCase().endsWith(".otf");
	}

	private void loadAndMonitorDir(AppContext ctx, UIToolkit<Font, ?> uiToolkit, Path fontsPath,
			boolean supplemental) throws IOException {
		ctx.monitor().monitor(fontsPath, cb -> {
			var fileChanged = fontsPath.resolve(cb.context());
			LOG.info("Watched file changed {}. {}", fileChanged, cb.kind());
			if (cb.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
				if (isFontFile(fileChanged)) {
					uiToolkit.runOnToolkitThread(() -> {
						loadFont(uiToolkit, supplemental, fileChanged);
						ctx.getConfiguration().terminal().putAll(Constants.FONTS_KEY, getFonts());
					});
				}
			} else if (cb.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
				if (isFontFile(fileChanged)) {
					var fnt = userFonts.remove(fileChanged.toString());
					if(fnt != null) {
						fontManager.removeFont(fnt);
						LOG.info("User font {} removed.", fileChanged);
						ctx.getConfiguration().terminal().putAll(Constants.FONTS_KEY, getFonts());
						fireFontsChanged();
					}
				}
			}
		});

		for (var fntfile : Files.newDirectoryStream(fontsPath, p -> isFontFile(p))) {
			loadFont(uiToolkit, supplemental, fntfile);
		}
	}

	private ManagedFont<Font,?> loadFont(UIToolkit<Font, ?> uiToolkit, boolean supplemental, Path fileChanged) {
		var fnt = userFonts.get(fileChanged.toString());
		try {
			if (fnt != null) {
				LOG.warn("Font at {} already registered.", fileChanged);
			} else {
				/* TODO: This deprecated toURL() works, but fileChanged.toUri().toString() does not, investigate */
				var tkFont = uiToolkit.loadFont(null, fileChanged.toFile().toURL().toString(), FontManager.DEFAULT_FONT_SIZE);
				fnt = new FontManager.ToolkitFont<Font>(uiToolkit, supplemental, tkFont);
				fontManager.addFont(0, fnt);
				
				
//				fnt = trueTypeFonts.addFontResource(null, fileChanged.toFile().toURL().toString(), FontManager.DEFAULT_FONT_SIZE, supplemental);
//				userFonts.put(fileChanged.toString(), fnt);
//				LOG.info("User font {} added.", fileChanged);
//				fireFontsChanged();
			}
		} catch (Exception ioe) {
			LOG.error("Failed to load font.", ioe);
		}
		return fnt;
	}
	
	private void fireFontsChanged() {
		for(int i = listeners.size() - 1 ; i >= 0 ; i--) 
			listeners.get(i).changed();
	}

	private void maybeMkdir(Path... paths) throws IOException {
		for(var path  : paths) {
			if (!Files.exists(path)) {
				Files.createDirectories(path);
			}
		}
	}
}
