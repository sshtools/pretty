package com.sshtools.pretty;

import static com.sshtools.terminal.emulation.VDUColor.fromString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.INI.Section;
import com.sshtools.terminal.emulation.Colors;
import com.sshtools.terminal.emulation.Colors.Size;
import com.sshtools.terminal.emulation.VDUColor;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

import javafx.scene.effect.Bloom;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.BoxBlur;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.effect.Glow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.effect.Lighting;
import javafx.scene.effect.MotionBlur;
import javafx.scene.effect.PerspectiveTransform;
import javafx.scene.effect.Reflection;
import javafx.scene.effect.SepiaTone;
import javafx.scene.effect.Shadow;

public class TerminalTheme {
	private static final String META = "meta";

	public static Logger LOG = LoggerFactory.getLogger(TerminalTheme.class);

	final static String BACKGROUND_COLOR = "bg";
	final static String FOREGROUND_COLOR = "fg";
	final static String DESCRIPTION = "description";
	final static String NAME = "name";
	final static String COLOR_PRINTING = "color-printing";
	final static String CURSOR_FOREGROUND = "cursor-fg";
	final static String CURSOR_BACKGROUND = "cursor-bg";
	final static String SELECTION_FOREGROUND = "selection-fg";
	final static String SELECTION_BACKGROUND = "selection-bg";
	final static String DARK_THEME = "dark-theme";
	final static String LIGHT_THEME = "light-theme";
	final static String LSCOLORS = "LSCOLORS";
	final static String LS_COLORS = "LS_COLORS";
	final static String EFFECT = "EFFECT";

	private static final String TYPE = "type";
	private static final String TYPE_STATIC = "static";
	private static final String TYPE_ABSTRACT = "abstract";
	private static final String TYPE_CONCRETE = "concrete";

	private final Section properties;
	private final AppContext app;

	TerminalTheme(AppContext app, Section properties) {
		this.app = app;
		this.properties = properties;
	}

	public void apply(JavaFXTerminalPanel terminalPanel, int bgAlphaPercent) {
		var vp = terminalPanel.getViewport();
		vp.enqueue(() -> {
			var colors = terminalPanel.getViewport().getColors();
			colors.setBG(background().withAlphaPercent(bgAlphaPercent));
			colors.setFG(foreground().withAlphaPercent(bgAlphaPercent));
			colors.setMouseBG(selectionBackground());
			colors.setMouseFG(selectionForeground());
			terminalPanel.setCursorColors(cursorForeground(), cursorBackground());
			vp.getColors().setPalette(Size.PAL16, pal16().orElseGet(() -> Colors.PAL16_DEFAULT.getColors()));
			var cols = vp.getColors();
			cols.setPalette(Size.PAL256, pal256().orElseGet(() -> Colors.PAL256_DEFAULT.getColors()));
			terminalPanel.getControl().setEffect(effect());
			vp.redisplay();
		});
	}

	public Effect effect() {
		var tprops = ini();
		var effects = tprops.getAllOr(EFFECT);
		Effect fx = null;
		if (effects.isPresent()) {
			for (var effect : effects.get()) {
				fx = createEffect(effect, fx);
			}
		}
		return fx;
	}

	private Effect createEffect(String spec, Effect input) {
		var args = parseArgs(spec);
		if (spec.equalsIgnoreCase("bloom")) {
			var blm = new Bloom();
			blm.setInput(input);
			if (args.size() > 0)
				blm.setThreshold(Double.parseDouble(args.get(0)));
			return blm;
		} else if (spec.equalsIgnoreCase("sepiaTone")) {
			var st = new SepiaTone();
			st.setInput(input);
			if (args.size() > 0)
				st.setLevel(Double.parseDouble(args.get(0)));
			return st;
		} else if (spec.equalsIgnoreCase("boxBlur")) {
			var bb = new BoxBlur();
			if (args.size() > 0 && !args.get(0).equals("")) {
				bb.setWidth(Double.parseDouble(args.get(0)));
			}
			if (args.size() > 1 && !args.get(1).equals("")) {
				bb.setHeight(Double.parseDouble(args.get(1)));
			}
			if (args.size() > 2 && !args.get(2).equals("")) {
				bb.setIterations(Integer.parseInt(args.get(2)));
			}
			bb.setInput(input);
			return bb;
		} else if (spec.equalsIgnoreCase("dropShadow")) {
			var ds = new DropShadow();
			ds.setInput(input);

			if (args.size() > 0 && !args.get(0).equals("")) {
				ds.setWidth(Double.parseDouble(args.get(0)));
			}
			if (args.size() > 1 && !args.get(1).equals("")) {
				ds.setHeight(Double.parseDouble(args.get(1)));
			}
			if (args.size() > 2 && !args.get(2).equals("")) {
				ds.setOffsetX(Double.parseDouble(args.get(2)));
			}
			if (args.size() > 3 && !args.get(3).equals("")) {
				ds.setOffsetY(Double.parseDouble(args.get(3)));
			}
			if (args.size() > 4 && !args.get(4).equals("")) {
				ds.setRadius(Double.parseDouble(args.get(4)));
			}
			if (args.size() > 5 && !args.get(5).equals("")) {
				ds.setSpread(Double.parseDouble(args.get(5)));
			}
			if (args.size() > 6 && !args.get(6).equals("")) {
				ds.setBlurType(BlurType.valueOf(args.get(6).toUpperCase()));
			}
			if (args.size() > 7 && !args.get(7).equals("")) {
				ds.setColor(app.getUiToolkit().localColorToNativeColor(fromString(args.get(7))));
			}
			return ds;
		} else if (spec.equalsIgnoreCase("gausianBlur")) {
			var gb = new GaussianBlur();
			if (args.size() > 0 && !args.get(0).equals("")) {
				gb.setRadius(0);
			}
			gb.setInput(input);
			return gb;
		} else if (spec.equalsIgnoreCase("glow")) {
			var gb = new Glow();
			if (args.size() > 0 && !args.get(0).equals("")) {
				gb.setLevel(Double.parseDouble(args.get(0)));
			}
			gb.setInput(input);
			return gb;
		} else if (spec.equalsIgnoreCase("innerShadow")) {
			var is = new InnerShadow();
			is.setInput(input);

			if (args.size() > 0 && !args.get(0).equals("")) {
				is.setWidth(Double.parseDouble(args.get(0)));
			}
			if (args.size() > 1 && !args.get(1).equals("")) {
				is.setHeight(Double.parseDouble(args.get(1)));
			}
			if (args.size() > 2 && !args.get(2).equals("")) {
				is.setOffsetX(Double.parseDouble(args.get(2)));
			}
			if (args.size() > 3 && !args.get(3).equals("")) {
				is.setOffsetY(Double.parseDouble(args.get(3)));
			}
			if (args.size() > 4 && !args.get(4).equals("")) {
				is.setRadius(Double.parseDouble(args.get(4)));
			}
			if (args.size() > 5 && !args.get(5).equals("")) {
				is.setChoke(Double.parseDouble(args.get(5)));
			}
			if (args.size() > 6 && !args.get(6).equals("")) {
				is.setBlurType(BlurType.valueOf(args.get(6).toUpperCase()));
			}
			if (args.size() > 7 && !args.get(7).equals("")) {
				is.setColor(app.getUiToolkit().localColorToNativeColor(fromString(args.get(7))));
			}
			return is;
		} else if (spec.equalsIgnoreCase("lighting")) {
			var lt = new Lighting();
			lt.setContentInput(input);
			if (args.size() > 0 && !args.get(0).equals("")) {
				lt.setDiffuseConstant(Double.parseDouble(args.get(0)));
			}
			if (args.size() > 1 && !args.get(1).equals("")) {
				lt.setSpecularConstant(Double.parseDouble(args.get(1)));
			}
			if (args.size() > 2 && !args.get(2).equals("")) {
				lt.setSpecularExponent(Double.parseDouble(args.get(2)));
			}
			if (args.size() > 3 && !args.get(3).equals("")) {
				lt.setSurfaceScale(Double.parseDouble(args.get(3)));
			}
			return lt;
		} else if (spec.equalsIgnoreCase("motionBlur")) {
			var mb = new MotionBlur();
			if (args.size() > 0 && !args.get(0).equals("")) {
				mb.setAngle(Double.parseDouble(args.get(0)));
			}
			if (args.size() > 1 && !args.get(1).equals("")) {
				mb.setRadius(Double.parseDouble(args.get(1)));
			}
			mb.setInput(input);
			return mb;
		} else if (spec.equalsIgnoreCase("perspectiveTransform")) {
			var mb = new PerspectiveTransform();
			if (args.size() > 0 && !args.get(0).equals("")) {
				mb.setLlx(Double.parseDouble(args.get(0)));
			}
			if (args.size() > 1 && !args.get(1).equals("")) {
				mb.setLly(Double.parseDouble(args.get(1)));
			}
			if (args.size() > 2 && !args.get(2).equals("")) {
				mb.setLrx(Double.parseDouble(args.get(2)));
			}
			if (args.size() > 3 && !args.get(3).equals("")) {
				mb.setLry(Double.parseDouble(args.get(3)));
			}
			if (args.size() > 4 && !args.get(4).equals("")) {
				mb.setUlx(Double.parseDouble(args.get(4)));
			}
			if (args.size() > 5 && !args.get(5).equals("")) {
				mb.setUly(Double.parseDouble(args.get(5)));
			}
			if (args.size() > 6 && !args.get(6).equals("")) {
				mb.setUrx(Double.parseDouble(args.get(6)));
			}
			if (args.size() > 7 && !args.get(7).equals("")) {
				mb.setUry(Double.parseDouble(args.get(7)));
			}
			mb.setInput(input);
			return mb;
		} else if (spec.equalsIgnoreCase("perspectiveTransform")) {
			var ref = new Reflection();
			if (args.size() > 0 && !args.get(0).equals("")) {
				ref.setBottomOpacity(Double.parseDouble(args.get(0)));
			}
			if (args.size() > 1 && !args.get(1).equals("")) {
				ref.setFraction(Double.parseDouble(args.get(1)));
			}
			if (args.size() > 2 && !args.get(2).equals("")) {
				ref.setTopOffset(Double.parseDouble(args.get(2)));
			}
			if (args.size() > 3 && !args.get(3).equals("")) {
				ref.setTopOpacity(Double.parseDouble(args.get(3)));
			}
			ref.setInput(input);
			return ref;
		} else if (spec.equalsIgnoreCase("shadow")) {
			var is = new Shadow();
			is.setInput(input);

			if (args.size() > 0 && !args.get(0).equals("")) {
				is.setWidth(Double.parseDouble(args.get(0)));
			}
			if (args.size() > 1 && !args.get(1).equals("")) {
				is.setHeight(Double.parseDouble(args.get(1)));
			}
			if (args.size() > 2 && !args.get(2).equals("")) {
				is.setRadius(Double.parseDouble(args.get(2)));
			}
			if (args.size() > 3 && !args.get(3).equals("")) {
				is.setBlurType(BlurType.valueOf(args.get(3).toUpperCase()));
			}
			if (args.size() > 4 && !args.get(4).equals("")) {
				is.setColor(app.getUiToolkit().localColorToNativeColor(fromString(args.get(4))));
			}
			return is;
		} else {
			throw new IllegalArgumentException("Unknown effect spec " + spec);
		}
	}

	private List<String> parseArgs(String spec) {
		if (spec.startsWith("(") && spec.endsWith(")")) {
			var val = Arrays.asList(spec.substring(1, spec.length() - 2).trim().split(","));
			var lst = val.stream().map(String::trim).toList();
			if (!lst.get(0).equals("")) {
				return lst;
			}
		}
		return Collections.emptyList();
	}

	public boolean isAbstract() {
		return getType().equals(TYPE_ABSTRACT);
	}

	public boolean isStatic() {
		return getType().equals(TYPE_STATIC);
	}

	public boolean isConcrete() {
		return getType().equals(TYPE_CONCRETE);
	}

	private String getType() {
		return ini().sectionOr(META).map(sec -> sec.get(TYPE, TYPE_STATIC)).orElse(TYPE_STATIC);
	}

	public Optional<String> darkModeTheme() {
		return ini().sectionOr(META).map(sec -> sec.get(DARK_THEME));
	}

	public Optional<String> lightModeTheme() {
		return ini().sectionOr(META).map(sec -> sec.get(LIGHT_THEME));
	}

	public String description() {
		return ini().sectionOr(META).map(sec -> sec.get(DESCRIPTION, id())).orElse(id());
	}

	public VDUColor background() {
		var tprops = ini();
		return tprops.getOr(BACKGROUND_COLOR).map(VDUColor::fromString).orElseGet(() -> VDUColor.BLACK);
	}

	public VDUColor cursorBackground() {
		var tprops = ini();
		return tprops.getOr(CURSOR_BACKGROUND).map(VDUColor::fromString).orElseGet(this::foreground);
	}

	public VDUColor cursorForeground() {
		var tprops = ini();
		return tprops.getOr(CURSOR_FOREGROUND).map(VDUColor::fromString).orElseGet(this::background);
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
		String id = id();
		if (id == null) {
			if (other.id() != null)
				return false;
		} else if (!id.equals(other.id()))
			return false;
		return true;
	}

	public VDUColor foreground() {
		var tprops = ini();
		return tprops.getOr(FOREGROUND_COLOR).map(VDUColor::fromString).orElseGet(() -> VDUColor.BLACK);
	}

	public String id() {
		return properties.key();
	}

	public String name() {
		return ini().sectionOr(META).map(sec -> sec.get(NAME, id())).orElse(id());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		String id = id();
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	public Section ini() {
		return properties;
	}

	public Optional<VDUColor[]> pal16() {
		var tprops = ini();
		var secOr = tprops.sectionOr("palette");
		if (secOr.isPresent()) {
			var sec = secOr.get();
			var allColors = new VDUColor[16];
			var sz = sec.keys().size();
			if (sec.contains("*")) {
				generate(sec, allColors, Colors.PAL16_DEFAULT.getColors());
				sz--;
			} else {
				System.arraycopy(Colors.PAL16_DEFAULT.getColors(), 0, allColors, 0, allColors.length);
			}
			if (sz != 16 && sz != 8 && sz != 0) {
				LOG.warn("If the 'palette' section is present, it must have ideally either 8 or 16 entries.");
			}
			sec.keys().stream().filter(k -> !k.equals("*")).forEach(k -> {
				try {
					var idx = Integer.parseInt(k);
					if (idx < 0 || idx > 16)
						throw new IllegalArgumentException("Index out of range.");
					allColors[idx] = fromString(sec.get(k));
				} catch (Exception e) {
					LOG.warn("Bad color entry or index @ {}. {}", k, e.getMessage());
				}
			});
			if (!sec.contains("*")) {
				for (int i = 8; i < 16; i++) {
					if (!sec.contains(String.valueOf(i))) {
						allColors[i] = allColors[i - 8].brighter();
					}
				}
			}
			return Optional.of(allColors);
		}
		return Optional.empty();

	}

	public Optional<VDUColor[]> pal256() {
		var tprops = ini();
		var secOr = tprops.sectionOr("palette-256");
		var newCols = new VDUColor[256];
		if (secOr.isPresent()) {
			var sec = secOr.get();
			if (sec.contains("*")) {
				generate(sec, newCols, Colors.PAL256_DEFAULT.getColors());
			} else {
				System.arraycopy(Colors.PAL256_DEFAULT.getColors(), 0, newCols, 0, newCols.length);
				pal16().ifPresent(pal16 -> {
					System.arraycopy(pal16, 0, newCols, 0, pal16.length);
				});
			}

			sec.keys().stream().filter(k -> !k.equals("*")).forEach(k -> {
				try {
					var idx = Integer.parseInt(k);
					if (idx < 0 || idx > 255)
						throw new IllegalArgumentException("Index out of range.");
					newCols[idx] = fromString(sec.get(k));
				} catch (Exception e) {
					LOG.warn("Bad color entry or index @ {}. {}", k, e.getMessage());
				}
			});
			return Optional.of(newCols);
		}
		else {
			var pal16 = pal16();
			if(pal16.isPresent()) {
				System.arraycopy(Colors.PAL256_DEFAULT.getColors(), 0, newCols, 0, newCols.length);
				System.arraycopy(pal16.get(), 0, newCols, 0, pal16.get().length);
				return Optional.of(newCols);
			}
		}
		return Optional.empty();

	}

	public VDUColor selectionBackground() {
		var tprops = ini();
		return tprops.getOr(SELECTION_BACKGROUND).map(VDUColor::fromString).orElseGet(this::foreground);
	}

	public VDUColor selectionForeground() {
		var tprops = ini();
		return tprops.getOr(SELECTION_FOREGROUND).map(VDUColor::fromString).orElseGet(this::background);
	}

	@Override
	public String toString() {
		return name();
	}

	protected void generate(Section sec, VDUColor[] allColors, VDUColor[] defaultCols) {
		var val = sec.get("*");
		var spec = new ArrayList<>(Arrays.asList(val.split("\\s+")));
		var firstVal = spec.remove(0);
		if (firstVal.equalsIgnoreCase("monochrome")) {
			var col = (spec.isEmpty() ? foreground() : fromString(spec.remove(0))).toHSL();
			var step = 251f / 8f;
			for (int i = 0; i < 8; i++) {
				var v = Math.round(i * step);
				allColors[i] = VDUColor.fromHSL(col[0], col[1], v / 255f);
			}
			for (int i = 0; i < 8; i++) {
				var v = 4 + Math.round(i * step);
				allColors[i + 8] = VDUColor.fromHSL(col[0], col[1], v / 255f);
			}
			step = 239f / 255f;
			for (int i = 16; i < allColors.length; i++) {
				var v = Math.round((i - 16) * step);
				allColors[i] = VDUColor.fromHSL(col[0], col[1], v / 255f);
			}
		} else {
			/* All same */
			var col = fromString(firstVal);
			for (int i = 0; i < allColors.length; i++) {
				allColors[i] = col;
			}
		}
	}

}
