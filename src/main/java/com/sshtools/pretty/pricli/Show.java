package com.sshtools.pretty.pricli;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.sshtools.jini.INIWriter;
import com.sshtools.jini.INIWriter.StringQuoteMode;
import com.sshtools.pretty.Configuration.SectionMeta;
import com.sshtools.pretty.Configuration.ValueMeta;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "show",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Shows configuration.")
public class Show implements Callable<Integer> {
	
	@Option(names = {"-s", "--section"}, paramLabel = "SECTION", description="The section under which to store the item. Nested sections are access using a dot notation, e.g. ssh.ciphers")
	private Optional<String> sectionPath;
	
	@Parameters(index = "0", arity="0..1", paramLabel="KEY", description = "Key")
	private Optional<String> key;
	
	@ParentCommand
	private PricliCommands parent;
	
	@Override
	public Integer call() throws Exception {
		var cfg = parent.ttyContext().getContainer().getConfiguration();
		var wtr = parent.cli().jline().writer();
		if(sectionPath.isEmpty()) {
			if(key.isEmpty()) {
				printSection(0, cfg.meta(), true);
			}
			else {
				wtr.println(MessageFormat.format("{0} = {1}", key.get(), cfg.get(key.get())));
			}
		}
		else {
			var section = cfg.section(sectionPath.get().split("\\."));
			if(key.isEmpty()) {
				printSection(0, section, false);
			}
			else {
				printValue(0, section.value(key.get()));
			}
		}
		return 0;
	}

	private void printSection(int depth, SectionMeta meta, boolean recurse) {
		var cli = parent.cli();
		var jline = cli.jline();
		var wtr = cli.jline().writer();
		if(meta.path().length > 0) {
			var as = new AttributedStringBuilder();
			as.style(AttributedStyle.BOLD);
			as.append(String.format(calcIndent(depth) + "[%s]", String.join(".", meta.path())));
			as.style(AttributedStyle.BOLD_OFF);
			as.println(jline);
		}
		meta.values().forEach(val -> printValue(depth, val));
		wtr.println();
		if(recurse) {
			meta.sections().forEach(sec -> printSection(depth + 1, sec, recurse));
		}
	}

	private void printValue(int depth, ValueMeta val) {
		var cli = parent.cli();
		var jline = cli.jline();
		var as = new AttributedStringBuilder();
		as.append(String.format(calcIndent(depth) + "%s = ", val.name()));
		switch(val.change()) {
		case DEFAULT:
			as.style(new AttributedStyle().faint());
			as.append(String.join(", ", INIWriter.quote('"', StringQuoteMode.AUTO, val.defaultValue())));
			as.style(new AttributedStyle().faintOff());
			break;
		case ADDED:
			as.style(AttributedStyle.BOLD);
			as.append(String.join(", ", INIWriter.quote('"', StringQuoteMode.AUTO, val.value())));
			as.style(AttributedStyle.BOLD_OFF);
			break;
		default:
			as.append(String.join(", ", INIWriter.quote('"', StringQuoteMode.AUTO, val.value())));
			break;
		}
		
		as.println(jline);
	}

	private String calcIndent(int depth) {
		var d = Math.max(0, ( depth - 1 ) * 2 );
		if(d == 0)
			return "";
		else
			return String.format("%" + d  + "s", "");
	}
}
