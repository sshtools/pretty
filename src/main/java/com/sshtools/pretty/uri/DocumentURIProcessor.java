package com.sshtools.pretty.uri;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import com.sshtools.pretty.Actions.Action;
import com.sshtools.pretty.Actions.ActionGroup;
import com.sshtools.pretty.Actions.ActionType;
import com.sshtools.pretty.Actions.On;
import com.sshtools.pretty.TTYContext;

import javafx.scene.input.KeyCombination;

public class DocumentURIProcessor implements URIProcessor {
	private final static ResourceBundle RESOURCES = ResourceBundle.getBundle(DocumentURIProcessor.class.getName());

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

	@Override
	public List<Action> actions(URI uri) {
		var lst = new ArrayList<Action>();
		var grp = new ActionGroup("uri", "URI", lst);
		lst.addAll(List.of(
			new Action(grp, 
					ActionType.COMMAND, 
					"copyUri", 
					RESOURCES.getString("copyUri.label"), 
					RESOURCES.getString("copyUri.description"), 
					"copy", 
					(KeyCombination)null, 
					new On[] { On.CONTEXT },
					new String[] { uri.toString() }),
			new Action(grp, 
					ActionType.COMMAND, 
					"open", 
					RESOURCES.getString("openUri.label"), 
					RESOURCES.getString("openUri.description"),
					"open", 
					(KeyCombination)null, 
					new On[] { On.CONTEXT },
					new String[] { uri.toString() })
		));
		return lst;
	}

}
