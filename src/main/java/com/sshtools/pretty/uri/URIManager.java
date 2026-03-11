package com.sshtools.pretty.uri;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import com.sshtools.pretty.Actions.Action;
import com.sshtools.pretty.TTYContext;
import com.sshtools.terminal.emulation.URIHandler;

public class URIManager implements URIHandler {

	private URI copiedUri;
	private URIProcessor selectedProcessor;
	private final Map<String, URIProcessor> processors = new HashMap<>();
	
	public URIManager(TTYContext ttyContext) {
		
		for(var proc : ServiceLoader.load(URIProcessor.class)) {
			proc.init(ttyContext);
			for(var scheme : proc.schemes()) {
				processors.put(scheme, proc);
			}
		}
	}
	
	public List<Action> actions() {
		return selectedProcessor != null && copiedUri != null ? selectedProcessor.actions(copiedUri) : List.of();
	}

	@Override
	public boolean clicked(URI uri, int button) throws Exception {
		if(selectedProcessor != null) {
			try {
				return selectedProcessor.clicked(uri, button);
			}
			finally {
				copiedUri = null;
				selectedProcessor = null;
			}
		}
		return false;
	}

	@Override
	public boolean pressed(URI uri, int button) {
		copiedUri = uri;
		selectedProcessor = processors.get(uri.getScheme());
		return false;
	}

}
