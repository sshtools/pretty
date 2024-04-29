package com.sshtools.pretty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.util.Calendar;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Reflectable;

@Bundle
@Reflectable(all = true)
public class AboutPane extends StackPane {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(AboutPane.class.getName());

	@FXML
	private Label version;

	@FXML
	private Label copyright;

	private final AppContext app;

	public AboutPane(AppContext app) {
		this.app = app;
		
		var loader = new FXMLLoader(getClass().getResource("AboutPane.fxml"));
		loader.setController(this);
		loader.setRoot(this);
		loader.setResources(RESOURCES);
		try {
			loader.load();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		
		version.setText(MessageFormat.format(RESOURCES.getString("version"), String.join(" ", Pretty.getVersion())));
		copyright.setText(
				MessageFormat.format(RESOURCES.getString("copyright"), String.valueOf(Calendar.getInstance().get(Calendar.YEAR))));

	}
	
	@FXML
	private void evtHomePage() {
		app.getHostServices().showDocument("https://jadaptive.com/pretty");
	}

}
