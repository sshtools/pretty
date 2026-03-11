package com.sshtools.pretty.uri;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import com.sshtools.pretty.TTYContext;
import com.sshtools.pretty.Actions.Action;
import com.sshtools.pretty.Actions.ActionGroup;
import com.sshtools.pretty.Actions.ActionType;
import com.sshtools.pretty.Actions.On;
import javafx.scene.input.KeyCombination;

public class MountURIProcessor implements URIProcessor {
	private final static ResourceBundle RESOURCES = ResourceBundle.getBundle(MountURIProcessor.class.getName());

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

	@Override
	public List<Action> actions(URI uri) {
		var lst = new ArrayList<Action>();
		var grp = new ActionGroup("uri", "URI", lst);
		lst.addAll(List.of(
			new Action(grp, 
					ActionType.COMMAND, 
					"mount", 
					RESOURCES.getString("mount.label"), 
					RESOURCES.getString("mount.description"), 
					"mount", 
					(KeyCombination)null, 
					new On[] { On.CONTEXT },
					new String[] { uri.toString() })
		));
		return lst;
	}
}
