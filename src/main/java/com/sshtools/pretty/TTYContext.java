package com.sshtools.pretty;

import javafx.stage.Stage;

public interface TTYContext {

	AppContext getContainer();

	void newTab();

	void newWindow();

	Stage stage();

}
