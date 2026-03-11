package com.sshtools.pretty.pricli;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.sshtools.pretty.Constants;
import com.sshtools.terminal.emulation.fonts.FontManager;
import com.sshtools.terminal.emulation.fonts.FontManager.ManagedFont;
import com.sshtools.terminal.emulation.fonts.FontSpec;
import com.sshtools.terminal.emulation.fonts.SoftFont;

import javafx.application.Platform;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Manage fonts used in the terminal. This allows listing of available fonts and
 * showing details of the currently active fonts. It also allows moving of fonts
 * to change the order and which font is used for which code points. Adding
 * fonts from those provided by the toolkit to this list of active fonts is also
 * supported, as is removal of such fonts.
 */
@Command(name = "fonts", aliases = {
		"f" }, footer = "%nAliases: f", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Show or change current fonts.")
public class Fonts implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Fonts.class.getName());

	public enum Operation {
		MOVE, ADD, REMOVE, SHOW, AVAILABLE, ACTIVE 
	}

	@ParentCommand
	private DebugCommands parent;

	@Parameters(index = "0", arity = "1", paramLabel = "OPERATION", description = """
			Use AVAILABLE to list fonts available from the toolkit and ACTIVE to list 
			currently active fonts. ADD, REMOVE and MOVE require a font name or number 
			as a parameter, with MOVE also requiring a destination position.
			""")
	private Operation operation = Operation.SHOW;

	@Parameters(index = "1", arity = "0..1", paramLabel = "NAME_OR_NUMBER", description = "Name or number of soft font to show.")
	private Optional<String> name;

	@Parameters(index = "2", arity = "0..1", paramLabel = "NUMBER", description = "Position to move the named or numbered font to.")
	private Optional<Integer> dest;

	@Override
	public Integer call() throws Exception {
		var tk = parent.ttyContext().getContainer().getUiToolkit();
		var jline = parent.cli().jline();
		var trm = parent.tty().terminal();
		var mgr = trm.getFontManager();
		var fonts = mgr.getFonts(true);
		var families = Arrays.asList(tk.getFonts());

		if (operation == Operation.ADD) {
			name.ifPresentOrElse(n -> {
				var fnt = findFont(mgr, families, n);
				Platform.runLater(() -> {
					mgr.addFont(fnt);
				});
				parent.cli().result(Styling.styled(MessageFormat.format(RESOURCES.getString("added"), fnt.getName())).toAttributedString());
			}, () -> {
				throw new IllegalArgumentException(RESOURCES.getString("nameOrNumberRequired"));
			});
		} else if (operation == Operation.REMOVE) {
			throw new UnsupportedOperationException("notImplemented");
		} else if (operation == Operation.MOVE) {
			name.ifPresentOrElse(n -> {
				var fnt = findManagedFont(mgr, fonts, n);
				if (dest.isPresent()) {
					var no = dest.get();
					if (no < 1 || no > fonts.size()) {
						throw new IllegalArgumentException(MessageFormat.format(RESOURCES.getString("invalidDestination"), n));
					}

					mgr.move(fnt, no - 1);

					parent.ttyContext().getContainer().getConfiguration().terminal().putAll(Constants.FONTS_KEY,
							mgr.getFonts(true).stream().map(f -> f.spec().getName()).toList().toArray(new String[0]));

					parent.cli().result(RESOURCES.getString("saved"));
				} else {
					throw new IllegalArgumentException(RESOURCES.getString("destinationRequired"));
				}

			}, () -> {
				throw new IllegalArgumentException(RESOURCES.getString("nameOrNumberRequired"));
			});
		} else if (operation == Operation.AVAILABLE) {
			printHeaderLine(jline, RESOURCES.getString("availableFonts"));
			var index = 0;
			for (var fnt : families) {
				++index;
				var as = appendFontSpec(index, fnt);
				as.println(jline);
			}
			
		}else if (operation == Operation.ACTIVE) {
			printHeaderLine(jline, RESOURCES.getString("currentFonts"));
			var index = 0;
			for (var fnt : fonts) {
				++index;
				var spec = fnt.spec();
				var as = appendFontSpec(index, spec);
				as.style(new AttributedStyle().faint());
				as.append(String.format("%-13s ", toFlags(fnt)));
				as.style(new AttributedStyle().faintOff());
				as.println(jline);
			}
			
		} else if (operation == Operation.SHOW) {

			name.ifPresentOrElse(n -> {
				var fnt = findManagedFont(mgr, fonts, n);

				printFontDetailLine(jline, fnt, RESOURCES.getString("name"), fnt.spec().getName());
				printFontDetailLine(jline, fnt, RESOURCES.getString("size"), String.valueOf(fnt.spec().getSize()));
				printFontDetailLine(jline, fnt, RESOURCES.getString("style"), toStyleString(fnt.spec().getStyle()));
				printFontDetailLine(jline, fnt, RESOURCES.getString("charSize"),
						String.format("%d x %d (%d)", fnt.charWidth(), fnt.charHeight(), fnt.charDescent()));
				printFontDetailLine(jline, fnt, RESOURCES.getString("flags"), toFlags(fnt));

				if (fnt.soft()) {
					var soft = (SoftFont) fnt.font();
					printFontDetailLine(jline, fnt, RESOURCES.getString("start"),
							String.valueOf(soft.getStartCodepoint()));
					printFontDetailLine(jline, fnt, RESOURCES.getString("end"),
							String.valueOf(soft.getEndCodepoint()));
				}
				
				
			}, () -> {
				throw new IllegalArgumentException(RESOURCES.getString("nameOrNumberRequired"));
			});
		}

		return 0;
	}

	private ManagedFont<?, ?> findManagedFont(FontManager<?> mgr, List<ManagedFont<?, ?>> fonts, String n) {
		var fnt = mgr.getFont(n);
		if (fnt == null) {
			try {
				fnt = fonts.get(Integer.parseInt(n) - 1);
			} catch (NumberFormatException nfe) {
			}
			if (fnt == null) {
				throw new IllegalArgumentException(MessageFormat.format("noSuchFont", n));
			}
		}
		return fnt;
	}

	private FontSpec findFont(FontManager<?> mgr, List<FontSpec> fonts, String n) {
		
		var fnt = fonts
				.stream()
				.filter(f -> f.getName().replace(" ", "").equalsIgnoreCase(n.replace(" ", "")))
				.findFirst().orElse(null);
		
		if (fnt == null) {
			try {
				fnt = fonts.get(Integer.parseInt(n) - 1);
			} catch (NumberFormatException nfe) {
			}
			if (fnt == null) {
				throw new IllegalArgumentException(MessageFormat.format("noSuchFont", n));
			}
		}
		return fnt;
	}

	private AttributedStringBuilder appendFontSpec(int index, FontSpec spec) {
		var as = new AttributedStringBuilder();
		as.style(new AttributedStyle().faint());
		as.append(String.format("%3d. ", index));
		as.style(new AttributedStyle().faintOff());
		as.append(String.format("%-35s", spec.getName()));
		as.style(AttributedStyle.BOLD);
		as.append(String.format("%2d  ", spec.getSize()));
		as.style(AttributedStyle.BOLD_OFF);
		as.style(new AttributedStyle().faint());
		as.append(String.format("%-13s ", toStyleString(spec.getStyle())));
		as.style(new AttributedStyle().faintOff());
		return as;
	}

	private void printHeaderLine(Terminal jline, String text) {
		var as = new AttributedStringBuilder();
		as.style(AttributedStyle.DEFAULT.underline());
		as.append(text);
		as.style(AttributedStyle.DEFAULT.underlineOff());
		as.println(jline);
	}

	private void printFontDetailLine(Terminal jline, ManagedFont<?, ?> fnt, String text, String value) {
		var as = new AttributedStringBuilder();
		as.style(AttributedStyle.BOLD);
		as.append(text);
		as.style(AttributedStyle.BOLD_OFF);
		as.append(": ");
		as.append(value);
		as.println(jline);
	}

	private String toFlags(ManagedFont<?, ?> fnt) {
		var slist = new ArrayList<String>();
		if (fnt.supplemental())
			slist.add(RESOURCES.getString("supplemental"));
		if (fnt.soft())
			slist.add(RESOURCES.getString("soft"));
		return String.join(",", slist);
	}

	private String toStyleString(int style) {
		var slist = new ArrayList<String>();
		if ((style & FontSpec.BOLD) != 0)
			slist.add(RESOURCES.getString("bold"));
		if ((style & FontSpec.ITALIC) != 0)
			slist.add(RESOURCES.getString("italic"));
		if (slist.isEmpty())
			slist.add(RESOURCES.getString("plain"));
		return String.join(",", slist);
	}
}
