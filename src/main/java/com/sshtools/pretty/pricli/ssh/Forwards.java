package com.sshtools.pretty.pricli.ssh;

import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.sshtools.common.forwarding.ForwardingHandle;
import com.sshtools.common.forwarding.ForwardingRequest.ForwardingType;
import com.sshtools.pretty.pricli.Styling;

import picocli.CommandLine.Command;

@Command(name = "forwards", aliases = { "fwds", "fws" }, usageHelpAutoWidth = true, description = "List all active port forwards.")
public class Forwards extends AbstractSelectForward {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Forwards.class.getName());

	@Override
	protected void matches(List<ForwardingHandle> fwds) {
		var jline = parent.cli().jline();
		var index = 0;
		for(var fwd : fwds) {
			var as = new AttributedStringBuilder();
			as.style(new AttributedStyle().faint());
			as.append(String.format("%3d. ", ++index));
			as.style(new AttributedStyle().faintOff());
			as.append(Styling.styled(MessageFormat.format(
					RESOURCES.getString(fwd.type() == ForwardingType.LOCAL ? "local" : "remote"),
					fwd.request().protocol(), 
					fwd.request().bindSpec(),  
					fwd.request().destinationSpec(),
					"")));
			
			as.println(jline);
		}		
		
	}

	@Override
	protected void noMatch() {
		if(type.isEmpty() && protocol.isEmpty() && identifiers.isEmpty()) {
			parent.cli().result(RESOURCES.getString("noForwards"));
		}
		else {
			parent.cli().result(RESOURCES.getString("noForwardsMatch"));	
		}
	}

}
