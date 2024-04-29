package com.sshtools.pretty;

import java.util.function.Supplier;

import com.sshtools.pretty.Actions.ActionGroup;
import com.sshtools.pretty.Actions.On;
import com.sshtools.pretty.pricli.PricliShell;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

public class ActionsContextMenu extends ContextMenu  {
	
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
		for(var act : actions.actions()) {
			
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
				shell.get().execute(act.fullCommand());
			});
			
			getItems().add(item);
		}
		onRebuild();
	}
	
	protected void onRebuild() {
	}

}
