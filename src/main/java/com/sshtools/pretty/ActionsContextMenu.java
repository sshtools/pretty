package com.sshtools.pretty;

import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.pretty.Actions.Action;
import com.sshtools.pretty.Actions.ActionGroup;
import com.sshtools.pretty.Actions.On;
import com.sshtools.pretty.pricli.PricliShell;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

public class ActionsContextMenu extends ContextMenu  {
	
	private final static Logger LOG = LoggerFactory.getLogger(ActionsContextMenu.class);
	
	private final Actions actions;
	private final Supplier<PricliShell> shell;
	private final On on;

	public ActionsContextMenu(On on, Actions actions, Supplier<PricliShell> shell) {
		
		this.on = on;
		this.actions = actions;
		this.shell = shell;

		getStyleClass().add("actions-context-menu");
		rebuild();
		
		setOnShowing(e -> rebuild());
	}

	private void rebuild() {
		ActionGroup grp = null;
		getItems().clear();
		for(var act : actions()) {
			
			if(!act.on(on))  
				continue;
			
			if(grp != null && !grp.equals(act.group())) {
				getItems().add(new SeparatorMenuItem());
			}
			grp = act.group();
			
			var item = new MenuItem(act.label());
			if(act.hasAccelerator())
				item.setAccelerator(act.accelerator());
			
			item.setOnAction(e -> {
				shell.get().tty().terminal().getViewport().getScheduler().submit(() -> {
					try {
						shell.get().execute(on, act.fullCommand());
					}
					catch(Throwable t) {
						LOG.error("Failed to execute action " + act.label(), t);
					}	
				});
			});
			
			getItems().add(item);
		}
		onRebuild();
	}

	protected List<Action> actions() {
		return actions.actions();
	}
	
	protected void onRebuild() {
	}

}
