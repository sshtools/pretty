package com.sshtools.pretty.pricli;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;

import com.sshtools.jini.INIWriter;
import com.sshtools.jini.schema.INISchema.KeyDescriptor;
import com.sshtools.jini.schema.INISchema.SectionDescriptor;
import com.sshtools.pretty.Configuration;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.ParseResult;

@Command(name = "options",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Shows options.")
public class Options implements Callable<Integer>, PricliCompleter {

	public record ConfigKey(String key,  String... section) {
		public static ConfigKey parse(String path) {
			var args = path.split("\\.");
			if(args.length == 1)
				return new ConfigKey(path);
			else {
				var a = new String[args.length - 1];
				System.arraycopy(args, 0, a, 0, args.length - 1);
				return new ConfigKey(args[0], a);
			}
				 
		}
	}
	
	@ArgGroup(multiplicity = "0..1")
    OptionsAction group = new OptionsAction();
	 

    static class OptionsAction {

    	@Option(order = 0, names = { "-v",
    			"--values" }, description = "Set, get or list individual options.")
    	private boolean values = true;

    	@Option(order = 1, names = { "-d",
    			"--dialog" }, description = "Open the dialog instead of printing application information here.")
    	private boolean dialog;

    	@Option(order = 2, names = { "-w",
    			"--write-ini" }, description = "Print out the full .ini file for the current configuration.")
    	private boolean writeIni;
    }
	
	@Parameters(index = "0", arity="0..1", paramLabel="KEY", description = """
			An (optionally) dot separated path to the configuration item. If no dots are 
			contained in the KEY then the configuration item must be unambiguous. 
			Otherwise, the section name should also be specified in the format <SECTIONPATH>.<KEY>.
			The section path itself may be a child section, again using the dot format <SECTIONPATH>.<CHILDSECTIONPATH>.<KEY>"
			""")
	private Optional<String> key;

	@Parameters(index = "1", arity="0..", paramLabel="KEY", description = """
		An (optionally) dot separated path to the configuration item. If no dots are 
		contained in the KEY then the configuration item must be unambiguous. 
		Otherwise, the section name should also be specified in the format <SECTIONPATH>.<KEY>.
		The section path itself may be a child section, again using the dot format <SECTIONPATH>.<CHILDSECTIONPATH>.<KEY>"
		""")
	private Optional<String> value;
	
	@ParentCommand
	private PricliCommands parent;

	@Override
	public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates, ParseResult parseResult, ArgSpec lastArg) {
		var cfg = parent.ttyContext().getContainer().getConfiguration();
		var schema = cfg.schema();
		System.out.println("---------------------");
//		System.out.println(schema.ini().toString());
		if(lastArg instanceof PositionalParamSpec pps) {
			Optional<String> kp = pps.getValue();
			schema.keyFromPath(kp.get()).ifPresent(kd -> {
				kd.values().stream().forEach(v -> Arrays.asList(v).forEach(cv ->  candidates.add(new Candidate(cv))));
			});
		}
//		else  {
////			if(lastArg instanceof PositionalParamSpec pps) {
////			if(pps.index().min() == 0)  {
//				schema.sections().forEach(sec -> {
//					candidates.add(new Candidate(sec.key()));
//					sec.keys().forEach(key -> {
//						var ck = new Candidate(String.join(".", sec.path()) + "." + key.key());
//						System.out.println(">> " + ck);
//						candidates.add(ck);
//					});
//				});
////			}
////				}
//		}
////		else {
////			candidates.add(new Candidate("SomeVal"));
////		}
		
	}

	@Override
	public Integer call() throws Exception {
		var cfg = parent.ttyContext().getContainer().getConfiguration();
		var trm = parent.cli().jline();
		var wtr = trm.writer();
		if(group.writeIni) {
			new INIWriter.Builder().build().write(cfg.document(), wtr);
		}
		else if(group.dialog) {
			parent.ttyContext().getContainer().options(parent.ttyContext().stage());
		}
		else {
			var schm = cfg.schema();
			
			
			SectionDescriptor grp = null;
			
			var sectionDescriptors = schm.section().sections();
			
			for(var sectionDescriptor : sectionDescriptors) {
				
				var dsb = new AttributedStringBuilder();
//				dsb.style(new AttributedStyle().underline());
//				dsb.style(new AttributedStyle().underlineOff());
				dsb.append('[');
				dsb.append(String.join(".", sectionDescriptor.path()));
				dsb.append("]");
				dsb.append("   ");
				dsb.append(sectionDescriptor.name());
				dsb.toAttributedString().println(trm);
				
				for(var key : sectionDescriptor.keys()) {
					printValue(cfg, trm, sectionDescriptor, key);
				}
				
				wtr.println();
			}
//			
			
			
//			cfg.
//			if(sectionPath.isEmpty()) {
//				if(key.isEmpty()) {
//					printSection(0, cfg.meta(), true);
//				}
//				else {
//					wtr.println(MessageFormat.format("{0} = {1}", key.get(), cfg.get(key.get())));
//				}
//			}
//			else {
//				var section = cfg.section(sectionPath.get().split("\\."));
//				if(key.isEmpty()) {
//					printSection(0, section, false);
//				}
//				else {
//					printValue(0, section.value(key.get()));
//				}
//			}
		}
		return 0;
	}

	protected void printValue(Configuration cfg, Terminal trm, SectionDescriptor sectionDescriptor, KeyDescriptor key) {
		var section = cfg.document().section(sectionDescriptor.path());
		
		var asb = new AttributedStringBuilder();
		asb.append(' ');
		asb.append(key.key());
		asb.append(" = ");
		asb.append(String.join(", ", section.getAllElse(key.key())));
		asb.append("   ");
		asb.append(key.name());
		asb.toAttributedString().println(trm);
	}

//	private void printSection(int depth, SectionMeta meta, boolean recurse) {
//		var cli = parent.cli();
//		var jline = cli.jline();
//		var wtr = cli.jline().writer();
//		if(meta.path().length > 0) {
//			var as = new AttributedStringBuilder();
//			as.style(AttributedStyle.BOLD);
//			as.append(String.format(calcIndent(depth) + "[%s]", String.join(".", meta.path())));
//			as.style(AttributedStyle.BOLD_OFF);
//			as.println(jline);
//		}
//		meta.values().forEach(val -> printValue(depth, val));
//		wtr.println();
//		if(recurse) {
//			meta.sections().forEach(sec -> printSection(depth + 1, sec, recurse));
//		}
//	}
//
//	private void printValue(int depth, ValueMeta val) {
//		var cli = parent.cli();
//		var jline = cli.jline();
//		var as = new AttributedStringBuilder();
//		
//		var wtr = new INIWriter.Builder().
//				withStringQuoteMode(StringQuoteMode.AUTO).
//				withEscapeMode(EscapeMode.ALWAYS).
//				build();
//		
//		as.append(String.format(calcIndent(depth) + "%s = ", val.name()));
//		switch(val.change()) {
//		case DEFAULT:
//			as.style(new AttributedStyle().faint());
//			as.append(String.join(", ", wtr.quote(val.defaultValue())));
//			as.style(new AttributedStyle().faintOff());
//			break;
//		case ADDED:
//			as.style(AttributedStyle.BOLD);
//			as.append(String.join(", ", wtr.quote(val.value())));
//			as.style(AttributedStyle.BOLD_OFF);
//			break;
//		default:
//			as.append(String.join(", ", wtr.quote(val.value())));
//			break;
//		}
//		
//		as.println(jline);
//	}
//
//	private String calcIndent(int depth) {
//		var d = Math.max(0, ( depth - 1 ) * 2 );
//		if(d == 0)
//			return "";
//		else
//			return String.format("%" + d  + "s", "");
//	}
}
