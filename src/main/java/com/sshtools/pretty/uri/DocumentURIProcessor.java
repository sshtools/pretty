package com.sshtools.pretty.uri;

import java.net.URI;

import com.sshtools.pretty.TTYContext;

public class DocumentURIProcessor implements URIProcessor {

	private TTYContext context;

	@Override
	public String[] schemes() {
		return new String[] { "file", "http", "https" };
	}

	@Override
	public void init(TTYContext context) {
		this.context = context;
	}

	@Override
	public boolean clicked(URI uri, int button) throws Exception {
		context.getContainer().getHostServices().showDocument(uri.toString());
		return true;
	}

}
