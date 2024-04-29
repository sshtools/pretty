package com.sshtools.pretty.pricli;

import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.sshtools.pretty.Actions.ActionGroup;

import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCombination.ModifierValue;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(name = "actions", aliases = {
		"act" }, footer = "%nAliases: act", mixinStandardHelpOptions = true, usageHelpAutoWidth = true, description = "Show available actions and the keys they are bound to.")
public class Actions implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Actions.class.getName());

	@ParentCommand
	private PricliCommands parent;

	@Option(names = { "-d",
			"--dialog" }, description = "Open the dialog instead of printing actions here.")
	private boolean dialog;

	@Option(names = { "-f",
			"--filter" },paramLabel = "FILTER",  description = "Only list action names or descriptions that contain FILTER.")
	private Optional<String> filter;

	@Override
	public Integer call() throws Exception {
		
		if (dialog) {
			parent.ttyContext().getContainer().actions(filter);
		} else {
			
			ActionGroup grp = null;
			
			var actions = parent.ttyContext().getContainer().getActions().actions();
			var trm = parent.cli().jline();
			var wtr = trm.writer();
			
			for(var act : actions) {
				if(!act.hasAccelerator()) 
					continue;
				
				var name = act.label();
				
				if(filter.isPresent() && !name.toLowerCase().contains(filter.get().toLowerCase()))
					continue;
				
				if(grp == null || !grp.equals(act.group())) {
					if(grp != null)
						wtr.println();
					
					grp = act.group();

					var dsb = new AttributedStringBuilder();
					dsb.style(new AttributedStyle().underline());
					dsb.append(act.group().heading());
					dsb.style(new AttributedStyle().underlineOff());
					dsb.toAttributedString().println(trm);
				}

				wtr.println();
				var dsb = new AttributedStringBuilder();
				dsb.append(' ');
				createKeys(dsb, act.accelerator());
				dsb.append(' ');
				dsb.append(name);
				dsb.toAttributedString().println(trm);
			}
			
			wtr.println();
		}
		return 0;
	}

	private void createKeys(AttributedStringBuilder dsb, KeyCombination keys) {
		if(keys.getControl() == ModifierValue.DOWN) {
			createKey(dsb, RESOURCES.getString("ctrl"));
		}
		if(keys.getAlt() == ModifierValue.DOWN) {
			printSeparator(dsb);
			createKey(dsb, RESOURCES.getString("alt"));
		}
		if(keys.getMeta() == ModifierValue.DOWN) {
			printSeparator(dsb);
			createKey(dsb, RESOURCES.getString("meta"));
		}
		if(keys.getShift() == ModifierValue.DOWN) {
			printSeparator(dsb);
			createKey(dsb, RESOURCES.getString("shift"));
		}
		if(keys.getShortcut() == ModifierValue.DOWN) {
			printSeparator(dsb);
			createKey(dsb, RESOURCES.getString("shortcut"));
		}
		if(keys instanceof KeyCodeCombination kce) {
			printSeparator(dsb);
			createKey(dsb, kce.getCode().name());
		}
		if(keys instanceof KeyCharacterCombination kce) {
			printSeparator(dsb);
			createKey(dsb, kce.getCharacter());
		}
	}

	protected void printSeparator(AttributedStringBuilder dsb) {
		if(dsb.length() > 1) {
			dsb.style(new AttributedStyle().faint());
			dsb.append("+");
			dsb.style(new AttributedStyle().faint());
		}
	}
	
	private void createKey(AttributedStringBuilder dsb, String text) {
		dsb.style(AttributedStyle.INVERSE);
		dsb.append(' ');
		dsb.append(text);
		dsb.append(' ');
		dsb.style(AttributedStyle.INVERSE_OFF);
	}
}
