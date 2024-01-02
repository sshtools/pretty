package com.sshtools.pretty;

import com.sshtools.terminal.vt.javafx.JavaFXAudioSystem;

public class TTYAudioSystem extends JavaFXAudioSystem {

	private TTY tty;

	public TTYAudioSystem(TTY tty) {
		this.tty = tty;
	}

	@Override
	protected void playBeep() {
		var aw = (PrettyAppWindow) tty.getTTYContext().appWindow();
		aw.animateBell();
		if (!tty.getTTYContext().getContainer().getConfiguration().getBoolean(Constants.MUTE_KEY, Constants.UI_SECTION)) {
			super.playBeep();
		}
	}

}
