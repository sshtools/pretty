package com.sshtools.pretty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ResourceBundle;

import com.sshtools.jajafx.UpdatePage;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Reflectable;

@Bundle
@Reflectable(all = true)
public class UpdateDialog extends DialogPane {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(UpdateDialog.class.getName());
	
	private final UpdatePage<PrettyApp> update;

	public UpdateDialog(AppContext app) {
		var loader = new FXMLLoader();
		loader.setResources(UpdatePage.RESOURCES); 
		try {
			var scene = new Scene(loader.load(UpdatePage.class.getResource("UpdatePage.fxml").openStream()));
			update = loader.getController();
			update.configure(scene, null, (PrettyApp) Pretty.getInstance().getFXApp());
			update.shown();
			setContent(scene.getRoot());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	public void onRemindMeTomorrow(Runnable r) {
		update.onRemindMeTomorrow(r);
	}

	public void hidden() {
		update.hidden();
	}

}
