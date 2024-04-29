package com.sshtools.pretty;

import java.io.Closeable;
import java.util.Optional;

import javafx.scene.Node;
import javafx.stage.Stage;

public interface TTYContext extends Closeable {
	
	Node content();

	AppContext getContainer();
	
	PrettyAppWindow appWindow();

	default void newTab() {
		newTab(new TTYRequest.Builder().build());
	}

	void newTab(TTYRequest request);

	void detachTab(TTY tty);

	default PrettyAppWindow newWindow() {
		return newWindow(Optional.of(new TTYRequest.Builder().build()));
	}

	PrettyAppWindow newWindow(Optional<TTYRequest> request);

	Stage stage();

	Optional<TTY> activeTty();

	void renameTab(TTY tty);
	
	void close();

}
