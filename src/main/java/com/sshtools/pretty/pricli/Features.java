package com.sshtools.pretty.pricli;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.sshtools.pretty.Strings;
import com.sshtools.terminal.emulation.Feature;
import com.sshtools.terminal.emulation.TerminalViewport;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "features", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Shows boolean terminal features.")
public class Features implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Features.class.getName());

	@Parameters(index = "0", arity = "0..1", paramLabel = "FEATURE", description = "Feature name")
	private Optional<String> key;
	
	@Parameters(index = "1", arity = "0..1", paramLabel = "STATE", description = "Boolean state, may be 'On' or 'Off', '0' or '1', 'true' or 'false', '+' or '-'")
	private Optional<String> state;

	@ParentCommand
	private Pricli.PricliCommands parent;

	@Override
	public Integer call() throws Exception {
		var vp = parent.tty().terminal().getViewport();
		var jline = parent.cli().jline();
		key.ifPresentOrElse(k -> {
			var feature = Feature.Registry.get().get(k).orElseThrow(() -> new IllegalArgumentException(MessageFormat.format(RESOURCES.getString("noFeature"), k)));
			state.ifPresentOrElse(s -> {
				var wantEnabled = Strings.parseBooleanSpec(s);
				var enabled = vp.enabled(feature);
				if(wantEnabled == enabled) {
					if(enabled)
						throw new IllegalStateException(RESOURCES.getString("alreadyEnabled"));
					else
						throw new IllegalStateException(RESOURCES.getString("alreadyDisabled"));
				}
				else {
					if(wantEnabled) {
						vp.enable(feature);
					}
					else {
						vp.disable(feature);
					}
					
					var attrStr = new AttributedStringBuilder();
					attrStr.style(new AttributedStyle().faint());
					attrStr.append(RESOURCES.getString(enabled ? "on" : "off"));
					attrStr.style(new AttributedStyle().faintOff());
					attrStr.style(AttributedStyle.BOLD);
					attrStr.append(" -> ");
					attrStr.style(AttributedStyle.BOLD_OFF);
					attrStr.append(RESOURCES.getString(wantEnabled ? "on" : "off"));
					
					parent.cli().result(attrStr.toAttributedString());
				}
			}, () -> {
				printFeature(vp, jline, feature);
			});
			
		}, () -> {
			for (var feature : Feature.Registry.get().features()) {
				printFeature(vp, jline, feature);
			}
		});
		return 0;

	}

	private void printFeature(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp, Terminal jline, Feature feature) {
		var enabled = vp.enabled(feature);
		var changed = enabled != feature.defaultState();
		var as = new AttributedStringBuilder();
		if (changed)
			as.style(AttributedStyle.BOLD);
		as.append(String.format("%-20s   ", feature.name()));
		if (changed)
			as.style(AttributedStyle.BOLD_OFF);
		if (enabled) {
			as.append(RESOURCES.getString("on"));
		} else {
			as.style(new AttributedStyle().faint());
			as.append(RESOURCES.getString("off"));
			as.style(new AttributedStyle().faintOff());
		}
		as.println(jline);
	}

}
