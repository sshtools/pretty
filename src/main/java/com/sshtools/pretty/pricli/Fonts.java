package com.sshtools.pretty.pricli;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.sshtools.pretty.Options;
import com.sshtools.terminal.emulation.fonts.FontManager.ManagedFont;
import com.sshtools.terminal.emulation.fonts.FontSpec;
import com.sshtools.terminal.emulation.fonts.SoftFont;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "fonts", aliases = {"f"}, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Show current fonts.")
public class Fonts implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Fonts.class.getName());

	@ParentCommand
	private PricliCommands parent;
	
	@Parameters(index = "0", arity="0..1", paramLabel="NAME_OR_NUMBER", description = "Name or number of soft font to show.")
	private Optional<String> name;
	
	@Parameters(index = "1", arity="0..1", paramLabel="NUMBER", description = "Position to move the named or numbered font to.")
	private Optional<Integer> dest;
	
	@Override
	public Integer call() throws Exception {
		var jline = parent.cli().jline();
		var trm = parent.tty().terminal();
		var mgr = trm.getFontManager();
		var fonts = mgr.getFonts(true);
		
		name.ifPresentOrElse(n -> {
			var fnt = mgr.getFont(n);
			if(fnt == null) {
				try {
					fnt = fonts.get(Integer.parseInt(n) - 1);
				}
				catch(NumberFormatException nfe) {
				}
				if(fnt == null) {
					throw new IllegalArgumentException(MessageFormat.format("noSuchFont", n));
				}
			}
			

			if(dest.isPresent()) {
				var no = dest.get();
				if(no < 1 || no > fonts.size()) {
					throw new IllegalArgumentException(MessageFormat.format("invalidDestination", n));
				}
				
				mgr.move(fnt, no - 1);
				
				parent.ttyContext().getContainer().getConfiguration().put("fonts", mgr.getFonts(true).stream().
						map(f -> f.spec().getName()).toList().toArray(new String[0]), Options.TERMINAL_SECTION);
				
				parent.cli().result(RESOURCES.getString("saved"));
			}
			else {
				printFontDetailLine(jline, fnt, RESOURCES.getString("name"), fnt.spec().getName());
				printFontDetailLine(jline, fnt, RESOURCES.getString("size"), String.valueOf(fnt.spec().getSize()));
				printFontDetailLine(jline, fnt, RESOURCES.getString("style"), toStyleString(fnt.spec().getStyle()));
				printFontDetailLine(jline, fnt, RESOURCES.getString("charSize"), String.format("%d x %d (%d)", fnt.charWidth(), fnt.charHeight(), fnt.charDescent()));
				printFontDetailLine(jline, fnt, RESOURCES.getString("flags"), toFlags(fnt));
				
				if(fnt.soft()) {
					var soft = (SoftFont)fnt.font();
					printFontDetailLine(jline, fnt, RESOURCES.getString("start"), String.valueOf(soft.getStartCodepoint()));
					printFontDetailLine(jline, fnt, RESOURCES.getString("end"), String.valueOf(soft.getEndCodepoint()));
				}
				
			}
			
		}, () -> {
			var index = 0;
			for(var fnt : fonts) {
				var as = new AttributedStringBuilder();
				as.style(new AttributedStyle().faint());
				as.append(String.format("%3d. ", ++index));
				as.style(new AttributedStyle().faintOff());
				as.append(String.format("%-35s", fnt.spec().getName()));
				as.style(AttributedStyle.BOLD);
				as.append(String.format("%2d  ", fnt.spec().getSize()));
				as.style(AttributedStyle.BOLD_OFF);
				as.style(new AttributedStyle().faint());
				as.append(String.format("%-13s ", toStyleString(fnt.spec().getStyle())));
				as.append(String.format("%-13s ", toFlags(fnt)));
				as.style(new AttributedStyle().faintOff());
				as.println(jline);
			}		
		});
		
		return 0;
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
		if(fnt.supplemental()) 
			slist.add(RESOURCES.getString("supplemental"));
		if(fnt.soft()) 
			slist.add(RESOURCES.getString("soft"));
		return String.join(",", slist);
	}

	private String toStyleString(int style) {
		var slist = new ArrayList<String>();
		if((style & FontSpec.BOLD) != 0) 
			slist.add(RESOURCES.getString("bold"));
		if((style & FontSpec.ITALIC) != 0) 
			slist.add(RESOURCES.getString("italic"));
		if(slist.isEmpty()) 
			slist.add(RESOURCES.getString("plain"));
		return String.join(",", slist);
	}
}
