package com.sshtools.pretty.uri;

import java.net.URI;
import java.util.List;

import com.sshtools.pretty.Actions.Action;
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
	
	/**
	 * Get the context menu actions for the given URI. This will be called when the user right-clicks on a link, and the returned actions will be shown in the context menu.
	 * @param uri uri for which to get the actions
	 * @return list of actions to show in the context menu, or empty list if no actions are available
	 */
	List<Action> actions(URI uri);

}
