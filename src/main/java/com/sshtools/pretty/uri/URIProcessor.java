package com.sshtools.pretty.uri;

import java.net.URI;

import com.sshtools.pretty.TTYContext;

public interface URIProcessor {
	/**
	 * @return URI schemes that this processor can handle, e.g. "http", "https",
	 *         "ftp"
	 */
	String[] schemes();

	/**
	 * Initialize the processor with the given context. This method will be called
	 * once when the processor is registered.
	 * 
	 * @param context context that can be used to interact with the terminal and
	 *                application environment
	 */
	void init(TTYContext context);

	/**
	 * Invoked when link is clicked.
	 * 
	 * @param uri    mouse button
	 * @param button mouse button
	 * @throws Exception on any error
	 */
	boolean clicked(URI uri, int button) throws Exception;

}
