package com.sshtools.pretty.pricli;

import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.sshtools.pretty.Strings;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "charmap", 
         aliases = {"cm"},
         footer = "%nAliases: cm",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Displays ranges of characters.")
public class Charmap implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Charmap.class.getName());

	@ParentCommand
	private DebugCommands parent;
	
	@Option(names = {"-d", "--decimal"}, description="Show decimal codes instead of hex.")
	private boolean decimal;
	
	@Parameters(index = "0", arity="0..1", paramLabel="CHARSPEC", description = "First character to show.")
	private Optional<String> start;
	
	@Parameters(index = "1", arity="0..1", paramLabel="CHARSPEC", description = "Last character to show.")
	private Optional<String> end;
	
	@Override
	public Integer call() throws Exception {
		var jline = parent.cli().jline();
		
		var wtr = jline.writer();
		var sval = start.map(Strings::parseCharSpec).orElse(32);
		var eval = end.map(Strings::parseCharSpec).orElse(256);
		
		var x = 0;
		var cw = 11;
		if(decimal) {
			cw = 13;
		}
		for(int c = sval ; c < eval; c++) {
			if(start.isEmpty() && end.isEmpty() && c > 128 && c < 160)
				continue;
			
			if(x >= jline.getWidth() - cw) {
				wtr.println();
				x = 0;
			}
			
			var as = new AttributedStringBuilder();
			as.style(AttributedStyle.BOLD);
			if(decimal)
				as.append(String.format("%7d  ", c));
			else
				as.append(String.format("%5x  ", c));
			as.style(AttributedStyle.BOLD_OFF);
			as.style(new AttributedStyle().faint());
			as.append((char)c);
			as.append("   ");
			as.style(new AttributedStyle().faintOff());
			as.print(jline);
			
			x += cw;
		}
		
		wtr.println();
		wtr.println();
		return 0;
	}
}
