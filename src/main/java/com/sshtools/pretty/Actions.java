package com.sshtools.pretty;

import static com.sshtools.jini.INI.merge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.Data;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.config.INISet;
import com.sshtools.jini.config.Monitor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.input.KeyCombination;

public class Actions extends AbstractINISetSystem {
	
	public enum On {
		CONTEXT, TAB, NEW_BUTTON, NONE, CLI_CONTEXT, TTY, CLI
	}

	private final static Logger LOG = LoggerFactory.getLogger(Actions.class);
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Actions.class.getName());

	public enum ActionType {
		COMMAND
	}
	
	public static record Action(
			ActionGroup group, 
			ActionType type, 
			String id, 
			String name, 
			String description, 
			String commandName, 
			KeyCombination accelerator, 
			On[] on, 
			String... args) {

		public String label() {
			return label(RESOURCES);
		}
		
		public String label(ResourceBundle resources) {
			try {
				return resources.getString(group.id() + "." + id + ".label");
			}
			catch(Exception e) {
				return name;
			}
		}
		
		public String fullCommand() {
			return Strings.formatQuotedArgs(commandName, args);
		}

		@Override
		public String toString() {
			return "Action [group=" + group.id + ", type=" + type + ", id=" + id + ", name=" + name + ", description="
					+ description + ", commandName=" + commandName + ", accelerator=" + accelerator + ", args="
					+ Arrays.toString(args) + "]";
		}

		public boolean hasAccelerator() {
			return accelerator != null;
		}

		public boolean on(On o) {
			return Arrays.asList(on).contains(o);
		}

		@Override
		public int hashCode() {
			return Objects.hash(id);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Action other = (Action) obj;
			return Objects.equals(id, other.id);
		} 
		
	}
	
	public static record ActionGroup(String id, String name, List<Action> actions) {

		public String heading() {
			return heading(RESOURCES);
		}
		
		public String heading(ResourceBundle resources) {
			try {
				return RESOURCES.getString(id + ".heading");
			}
			catch(Exception e) {
				return name;
			}
		}

		@Override
		public int hashCode() {
			return Objects.hash(id);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ActionGroup other = (ActionGroup) obj;
			return Objects.equals(id, other.id);
		}
	}
	
	private ObservableList<Action> actions = FXCollections.observableArrayList();
	
	public Actions(AppContext app, Monitor monitor) {
		super(app, "actions.d");
		loadActionsFromIni(new INISet.Builder("actions").
				withMonitor(monitor).
				withDefault(Shells.class, "Actions.ini").
				build().document());
	}
	
	public ObservableList<Action> actions() {
		return actions;
	}
	
	private void addAction(Action action, List<Action> actions) {
		actions.add(action);
		this.actions.add(action);
	}

	private void loadActionsFromIni(Data ini) {
		for(var sec : ini.allSections()) {
			try {
				var name = sec.get("name", sec.key());
				var ag =new ActionGroup(sec.key(), name, new ArrayList<>());
				for(var actsec : sec.allSections()) {
					if(isForOs(sec)) {
						addAction(actionFromSection(ag, actsec), ag.actions);
					}
				}
			}  catch (Exception e) {
				LOG.debug("Failed to load shell.", e);
			}
		}
	}
	
	private Action actionFromSection(ActionGroup grp, Section section) {
		return new Action(
			grp,
			ActionType.COMMAND,
			section.key(),
			section.get("name", section.key()),
			section.getOr("description").orElse(null),
			section.getOr("command").orElse(section.key()),
			section.getOr("accelerator").map(KeyCombination::keyCombination).orElse(null),
			Arrays.asList(section.getAllElse("on", On.TTY.name())).stream().map(s -> On.valueOf(Strings.toEnumName(s))).toList().toArray(new On[0]),
			merge(
					section.getAllElse("argument"),
					section.getAllElse("arguments")
			)
		);
	}
}
