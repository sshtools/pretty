package com.sshtools.pretty.pricli;

import static javafx.application.Platform.runLater;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.sshtools.pretty.Configuration.SectionMeta;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "set",commandListHeading = "CLIST",  usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Set values in the configuration file.")
public class SetConfig implements Callable<Integer> {
	
	@Option(names = {"-s", "--section"}, paramLabel = "SECTION", description="The section under which to store the item. Nested sections are access using a dot notation, e.g. ssh.ciphers")
	private Optional<String> sectionPath;
	
	@Option(names = {"-c", "--create-section"}, description="Force creation of a section. Some well defined sections will be created automatically.")
	private boolean createSection;
	
	@Parameters(index = "0", arity="1", paramLabel="KEY", description = "Key")
	private String key;
	
	@Parameters(index = "1", arity="0..1", paramLabel="VALUE", description = "Value. Omit to delete if key exists.")
	private Optional<String> value;
	
	@ParentCommand
	private PricliCommands parent;
	
	@Override
	public Integer call() throws Exception {
		var cfg = parent.ttyContext().getContainer().getConfiguration();
		var tty = parent.tty();
		
		SectionMeta where = cfg.meta();
		if(sectionPath.isPresent()) {
			var path = sectionPath.get().split("\\.");
			var secMeta = cfg.sectionOr(path);
			if(secMeta.isPresent()) 
				where = secMeta.get();
			else {
				if(createSection) {
					where = cfg.create(path);
				}
				else
					throw new IllegalStateException(MessageFormat.format("The section ''{0}'' does not exist, are you sure this is the correct path?. You may force creation using the --create-section option.", sectionPath.get()));
			}
		}

		var attrStr = new AttributedStringBuilder();
		var wherePath = where.path();
		where.valueOr(key).ifPresentOrElse(w -> {
			var was = cfg.get(key, wherePath);
			if(value.isPresent()) {
				cfg.put(key, value.get(), wherePath);
				attrStr.style(new AttributedStyle().faint());
				attrStr.append(was);
				attrStr.style(new AttributedStyle().faintOff());
				attrStr.style(AttributedStyle.BOLD);
				attrStr.append(" -> ");
				attrStr.style(AttributedStyle.BOLD_OFF);
				attrStr.append(value.get());
			}
			else {
				cfg.remove(key, wherePath);
				attrStr.style(AttributedStyle.BOLD);
				attrStr.append("- ");
				attrStr.style(AttributedStyle.BOLD_OFF);
				attrStr.style(new AttributedStyle().faint());
				attrStr.append(was);
				attrStr.style(new AttributedStyle().faintOff());
			}
		}, () -> {
			if(value.isPresent()) {
				cfg.put(key, value.get(), wherePath); 
				attrStr.style(AttributedStyle.BOLD);
				attrStr.append("+ ");
				attrStr.style(AttributedStyle.BOLD_OFF);
				attrStr.append(value.get());
			}
			else {
				throw new IllegalStateException("No such key.");
			}
		});
		
		parent.cli().result(attrStr.toAttributedString());
		runLater(() -> parent.cli().terminal().focusTerminal());
		
		return 0;
	}
}
