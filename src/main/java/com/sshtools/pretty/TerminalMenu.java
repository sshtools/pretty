package com.sshtools.pretty;

import java.util.Collection;
import java.util.Collections;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCombination;

public final class TerminalMenu {

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(TerminalMenu.class.getName());
	private ContextMenu menu;

	public TerminalMenu(JavaFXTerminalPanel terminalPanel, Consumer<ClipboardContent> onCopy, Consumer<Clipboard> onPaste, Runnable toggle, MenuItem... accessoryItems) {
		this(terminalPanel, onCopy, onPaste, Collections.emptyList(), toggle, accessoryItems);
	}
	
	public TerminalMenu(JavaFXTerminalPanel terminalPanel, Consumer<ClipboardContent> onCopy, Consumer<Clipboard> onPaste, Collection<MenuItem> top, Runnable toggle, MenuItem... accessoryItems) {

		var selectAllMenuItem = new MenuItem(RESOURCES.getString("selectAll"));
		selectAllMenuItem.setAccelerator(KeyCombination.keyCombination("CTRL+SHIFT+A"));
		selectAllMenuItem.setOnAction((e) -> { 
			synchronized(terminalPanel.getViewport().getBufferLock()) {
				terminalPanel.getViewport().selectAll(); 
			}
		});

		var copyMenuItem = new MenuItem(RESOURCES.getString("copy"));
		copyMenuItem.setAccelerator(KeyCombination.keyCombination("CTRL+SHIFT+C"));
		copyMenuItem.setOnAction((e) -> {
			var content = new ClipboardContent();
			synchronized(terminalPanel.getViewport().getBufferLock()) {
				content.putString(terminalPanel.getViewport().getSelection());
			};
			onCopy.accept(content);
		});

		var pasteMenuItem = new MenuItem(RESOURCES.getString("paste"));
		pasteMenuItem.setAccelerator(KeyCombination.keyCombination("CTRL+SHIFT+A"));
		pasteMenuItem.setOnAction((e) -> { 
			onPaste.accept(Clipboard.getSystemClipboard());
		});

		var pricliMenuItem = new MenuItem(RESOURCES.getString("pricli"));
		pricliMenuItem.setAccelerator(KeyCombination.keyCombination("ALT+/"));
		pricliMenuItem.setOnAction(e -> toggle.run());

		menu = new ContextMenu();
		menu.getItems().addAll(top);
		menu.getItems().addAll(copyMenuItem, pasteMenuItem, new SeparatorMenuItem(), selectAllMenuItem);
		if(accessoryItems.length > 0) {
			menu.getItems().add(new SeparatorMenuItem());
			menu.getItems().addAll(accessoryItems);
		}
		menu.getItems().addAll(new SeparatorMenuItem(), pricliMenuItem);
	}

	public ContextMenu menu() {
		return menu;
	}
}
