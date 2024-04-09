package com.sshtools.pretty;

import java.util.Optional;

import javafx.stage.Stage;

public interface TTYContext {

	AppContext getContainer();
	
	PrettyAppWindow appWindow();

	void newTab();

	void newWindow();

	Stage stage();

	Optional<TTY> activeTty();

}
