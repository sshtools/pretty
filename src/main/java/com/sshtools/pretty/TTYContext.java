package com.sshtools.pretty;

import java.util.Optional;

import com.sshtools.jajafx.JajaFXAppWindow;

import javafx.stage.Stage;

public interface TTYContext {

	AppContext getContainer();
	
	JajaFXAppWindow appWindow();

	void newTab();

	void newWindow();

	Stage stage();

	Optional<TTY> activeTty();

}
