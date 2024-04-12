package com.sshtools.pretty;

import java.util.Optional;

import javafx.scene.Node;
import javafx.stage.Stage;

public interface TTYContext {
	
	Node content();

	AppContext getContainer();
	
	PrettyAppWindow appWindow();

	void newTab();

	void detachTab(TTY tty);

	PrettyAppWindow newWindow();

	Stage stage();

	Optional<TTY> activeTty();

}
