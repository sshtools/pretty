package com.sshtools.pretty;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.ResourceBundle;

import com.sshtools.jajafx.FXUtil;
import com.sshtools.pretty.Actions.Action;
import com.sshtools.pretty.Actions.ActionGroup;

import javafx.beans.property.BooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCombination.ModifierValue;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Reflectable;

@Bundle
@Reflectable(all = true)
public class ActionsPane extends StackPane implements Closeable {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(ActionsPane.class.getName());

	private final AppContext app;
	
	@FXML
	private TextField search;
	
	@FXML
	private VBox items;

	private ListChangeListener<? super Action> lsntr;

	public ActionsPane(AppContext app, Optional<String> filter) {
		this.app = app;
		
		var loader = new FXMLLoader(getClass().getResource("ActionsPane.fxml"));
		loader.setController(this);
		loader.setRoot(this);
		loader.setResources(RESOURCES);
		try {
			loader.load();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		
		search.textProperty().addListener((c,o,n) -> {
			FXUtil.maybeQueue(() -> rebuild());
		});
		
		lsntr = (e) -> rebuild();
		filter.ifPresent(search::setText);
		search.managedProperty().bind(search.visibleProperty());
		search.visibleProperty().addListener((c,o,n) -> {
			if(n)
				search.requestFocus();
		});
		search.setVisible(false);
		
		this.app.getActions().actions().addListener(lsntr);

		rebuild();
	}
	
	public BooleanProperty searchVisibleProperty() {
		return search.visibleProperty();
	}

	@Override
	public void close() {
		this.app.getActions().actions().addListener(lsntr);
	}
	
	@FXML
	public void dropInActions(ActionEvent evt) {
		app.getHostServices().showDocument(app.getActions().getDropInPath().toUri().toString()); 
	}

	private HBox createKeys(KeyCombination keys) {
		var hb = new HBox();
		hb.setPrefWidth(260);
		hb.getStyleClass().add("spaced");
		var children = hb.getChildren();
		if(keys.getControl() == ModifierValue.DOWN) {
			children.add(createKey(RESOURCES.getString("ctrl")));
		}
		if(keys.getAlt() == ModifierValue.DOWN) {
			if(!children.isEmpty())
				children.add(new Label("+"));
			children.add(createKey(RESOURCES.getString("alt")));
		}
		if(keys.getMeta() == ModifierValue.DOWN) {
			if(!children.isEmpty())
				children.add(new Label("+"));
			children.add(createKey(RESOURCES.getString("meta")));
		}
		if(keys.getShift() == ModifierValue.DOWN) {
			if(!children.isEmpty())
				children.add(new Label("+"));
			children.add(createKey(RESOURCES.getString("shift")));
		}
		if(keys.getShortcut() == ModifierValue.DOWN) {
			if(!children.isEmpty())
				children.add(new Label("+"));
			children.add(createKey(RESOURCES.getString("shortcut")));
		}
		if(keys instanceof KeyCodeCombination kce) {
			if(!children.isEmpty())
				children.add(new Label("+"));
			children.add(createKey(kce.getCode().name()));
		}
		if(keys instanceof KeyCharacterCombination kce) {
			if(!children.isEmpty())
				children.add(new Label("+"));
			children.add(createKey(kce.getCharacter()));
		}
		return hb;
	}
	
	private Label createKey(String text) {
		var lbl = new Label(text);
		lbl.getStyleClass().add("action-key");
		return lbl;
	}
	
	private void rebuild() {
		items.getChildren().clear();
		
		ActionGroup grp = null;
		var filter = search.getText().toLowerCase();
		
		for(var act : app.getActions().actions()) {
			if(!act.hasAccelerator()) 
				continue;
			
			var name = act.label();
			
			if(!filter.equals("") && !name.toLowerCase().contains(filter))
				continue;
			
			if(grp == null || !grp.equals(act.group())) {
				grp = act.group();
				var lbl = new Label(act.group().heading());
				lbl.getStyleClass().add("tpad");
				lbl.getStyleClass().add("h3");
				items.getChildren().add(lbl);
			}
			
			var bp = new HBox();
			bp.getStyleClass().add("lpad");
			bp.getChildren().add(createKeys(act.accelerator()));
			
			var lbl = new Label(name);
			lbl.setAlignment(Pos.CENTER_LEFT);
			bp.getChildren().add(lbl);
			
			items.getChildren().add(bp);
		}
	}

}
