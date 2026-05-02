package com.sshtools.pretty.pricli;

import java.util.Arrays;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.sshtools.terminal.api.InputModifier;
import com.sshtools.terminal.api.KeyMap;
import com.sshtools.terminal.dec.DECEmulator;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(name = "keymap", 
         aliases = {"km"},
         footer = "%nAliases: ks",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Displays current emulator key mappings.")
public class Keys implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Keys.class.getName());

	@ParentCommand
	private TerminalCommands parent;
	
	@Override
	public Integer call() throws Exception {
		var jline = parent.cli().jline();
		var keymap = parent.tty().terminal().getViewport().getDeviceInput().get().getKeyMap();
		 
		keymap.getKeyMappings().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach((entry) -> {
			var keySeq = entry.getKey();
			var as = new AttributedStringBuilder();
			var mods = Arrays.asList(InputModifier.decode(keySeq.modifiers()));
			var kstr = String.join(",", mods.stream().map(InputModifier::name).toList());
			
			as.style(AttributedStyle.BOLD);
			as.append(String.format("%-20s %-30s", keySeq.key(), kstr));
			as.style(AttributedStyle.BOLD_OFF);
			as.style(new AttributedStyle().faint());
			as.append(KeyMap.escape(entry.getValue(), ((DECEmulator<?>)parent.tty().terminal().getViewport()).isOutput8bit()));
			as.style(new AttributedStyle().faintOff());
			as.println(jline);
		});
		return 0;
	}
}
