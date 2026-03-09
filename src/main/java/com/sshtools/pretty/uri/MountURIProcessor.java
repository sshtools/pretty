package com.sshtools.pretty.uri;

import java.net.URI;

import com.sshtools.pretty.TTYContext;

public class MountURIProcessor implements URIProcessor {

	@Override
	public String[] schemes() {
		return new String[] { "local-sftp" };
	}

	@Override
	public void init(TTYContext context) {
	}

	@Override
	public boolean clicked(URI uri, int button) throws Exception {
		return false;
	}

}
