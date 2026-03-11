package com.sshtools.pretty.pricli.ssh;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.common.forwarding.ForwardingHandle;

import picocli.CommandLine.Command;

@Command(name = "stop-forwards", aliases = { "sf", "stop-forward" }, usageHelpAutoWidth = true, description = "Stop any or all active port forwards.")
public class StopForwards extends AbstractSelectForward {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(StopForwards.class.getName());
	
	private final static Logger LOG = LoggerFactory.getLogger(StopForwards.class);

	@Override
	protected void matches(List<ForwardingHandle> fwds) {
		var exceptions = new ArrayList<IOException>();
		for(var fwd : fwds) {
			try {
				fwd.close();
			} catch (IOException e) {
				exceptions.add(e);
				if(LOG.isDebugEnabled()) {
					LOG.debug("Failed to stop forward.", e);
				}
				
			}
		}
		if(exceptions.isEmpty()) {
			parent.cli().result(MessageFormat.format(RESOURCES.getString("stoppedForwardsWithErrors"), fwds.size()));
		}
		else {
			if(exceptions.size() == 1) {
				throw new IllegalStateException(MessageFormat.format(RESOURCES.getString("stoppedForwardsWithError"), exceptions.get(0).getMessage(), exceptions.get(0)));
			}
			else {
				throw new IllegalStateException(MessageFormat.format(RESOURCES.getString("stoppedForwardsWithErrors"), exceptions.size()));
			}
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
