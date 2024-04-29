package com.sshtools.pretty.pricli;

import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.sshtools.jini.INIWriter;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(name = "themes", 
         aliases = {"ts"},
         footer = "%nAliases: ts",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Show terminal themes.")
public class Themes implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Themes.class.getName());

	@ParentCommand
	private TerminalCommands parent;

	@Option(names = { "-w",
			"--write-ini" }, description = "Print out the full .ini file for the current themes.")
	private boolean writeIni;
	
	@Override
	public Integer call() throws Exception {
		var jline = parent.cli().jline();
		var wtr = jline.writer();
		if(writeIni) {
			new INIWriter.Builder().build().write(parent.ttyContext().getContainer().getThemes().document(), wtr);
		}
		else {
			var index = 0;
			for(var fnt : parent.ttyContext().getContainer().getThemes().getAll()) {
				var as = new AttributedStringBuilder();
				var bg = fnt.background();
				var fg = fnt.foreground();
				
				as.style(new AttributedStyle().faint());
				as.append(String.format("%3d. ", ++index));
				as.style(new AttributedStyle().faintOff());
				as.append(String.format("%-35s", fnt.name()));
				as.append("B: ");
				
				as.style(new AttributedStyle().backgroundRgb(bg.toInt()).foregroundRgb(fg.toInt()));
				as.append(bg.toHTMLColor());
				as.style(new AttributedStyle().backgroundOff().foregroundOff());
				as.append("  F: ");
				as.style(new AttributedStyle().backgroundRgb(fg.toInt()).foregroundRgb(bg.toInt()));
				as.append(fg.toHTMLColor());
				as.style(new AttributedStyle().backgroundOff());
				as.println(jline);
			}	
		}
		
		return 0;
	}


}
