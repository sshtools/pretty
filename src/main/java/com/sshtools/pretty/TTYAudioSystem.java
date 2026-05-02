package com.sshtools.pretty;

import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.terminal.api.AudioSystem;
import com.sshtools.terminal.audio.JavaSoundAudioSystem;
import com.sshtools.terminal.vt.javafx.JavaFXAudioSystem;

public class TTYAudioSystem implements AudioSystem {

	static Logger LOG = LoggerFactory.getLogger(TTYAudioSystem.class);

	private TTY tty;
	private JavaSoundAudioSystem javaSoundAudioSystem;
//	private JavaFXAudioSystem javafxAudioSystem;

	public TTYAudioSystem(TTY tty) {
		this.tty = tty;
		javaSoundAudioSystem = new JavaSoundAudioSystem();
//		javafxAudioSystem = new JavaFXAudioSystem();
	}

	@Override
	public void beep() {
//		if(!isMuted())
//			javafxAudioSystem.beep();
	}

	@Override
	public void setBeepAudioResource(URL beepAudioResource) {
//		javafxAudioSystem.setBeepAudioResource(beepAudioResource);
	}

	@Override
	public URL getBeepAudioResource() {
//		return javafxAudioSystem.getBeepAudioResource();
		return null;
	}

	@Override
	public void close() {
		try {
			javaSoundAudioSystem.close();
		}
		finally {
//			javafxAudioSystem.close();
		}
	}

	@Override
	public void playNote(Note note) {
		/*
		 * TODO may need to do better. This wont necessarily muted immediately, and may
		 * result in a note being played after the user has muted the emulator.
		 */
		if(!isMuted())
			javaSoundAudioSystem.playNote(note);
	}
//	@Override
//	public void playNote(Note note, int channel) {
//		if(!isMuted())
//			javaSoundAudioSystem.playNote(note, channel);
//	}

	protected boolean isMuted() {
		return tty.ttyContext().getContainer().getConfiguration().ui().getBoolean(Constants.MUTE_KEY);
	}

	@Override
	public void click(int volume) {
//		javafxAudioSystem.click(volume);
	}

	@Override
	public void setClickAudioResource(URL clickAudioResource) {
//		javafxAudioSystem.setClickAudioResource(clickAudioResource);
	}

	@Override
	public URL getClickAudioResource() {
//		return javafxAudioSystem.getClickAudioResource();
		return null;
	}

}
