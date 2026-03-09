package com.sshtools.pretty.pricli.ssh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.TimeoutException;

import org.cryptomator.jfuse.api.Fuse;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.client.SshClient;
import com.sshtools.common.events.Event;
import com.sshtools.common.events.EventCodes;
import com.sshtools.common.events.EventListener;
import com.sshtools.common.ssh.SshConnection;
import com.sshtools.pretty.Strings;
import com.sshtools.pretty.TTY;
import com.sshtools.pretty.Status.Element;
import com.sshtools.pretty.Status.Unit;
import com.sshtools.pretty.Status.Width;
import com.sshtools.pretty.ssh.SFTPFileSystem;
import com.sshtools.terminal.emulation.TerminalViewport;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

record ActiveMount(String name, TTY tty, SFTPFileSystem fs, SshClient sshClient, Fuse fuse, Thread shutdownHook, Path mountPoint) implements EventListener, Element {

	final static Logger LOG = LoggerFactory.getLogger(ActiveMount.class);
	final static Map<String, ActiveMount> mounts = new HashMap<>();
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(ActiveMount.class.getName());
	
	public ActiveMount unmount() {

		LOG.info("Unmounting {} from {}...", name, mountPoint);
		try {
			sshClient.getConnection().removeEventListener(this);
			fuse.close();
			Files.deleteIfExists(mountPoint);
			LOG.info("Unmounted {} from {}.", name, mountPoint);
		} catch (TimeoutException | IOException e) {
			LOG.error("Failed to unmount.", e);
		} finally {
			mounts.remove(name);
			Runtime.getRuntime().removeShutdownHook(shutdownHook);
			tty.status().remove(this);
		}
		return this;
	}

	@Override
	public void processEvent(Event evt) {
		if (evt.getId() == EventCodes.EVENT_DISCONNECTED) {
			var con = (SshConnection) evt.getAttribute(EventCodes.ATTRIBUTE_CONNECTION);
			mounts.entrySet().stream().filter(e -> e.getValue().sshClient.getConnection().equals(con))
					.findFirst().map(Map.Entry::getValue).ifPresent(ActiveMount::unmount);;
		}
		
	}

	@Override
	public Width width() {
		return new Width(Unit.REMAINING, 0);
	}

	@Override
	public void draw(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp, int cols) throws IOException {
		var bldr = new AttributedStringBuilder();
		bldr.style(AttributedStyle.INVERSE);
		var url = mounts.size() == 1 
				? mountPoint.toUri().toString() 
				: mountPoint.getParent().toUri().toString();
		bldr.append(Strings.link(tty().cli().jline(), url, Strings.trimPad(String.format("%s", getMountText()), cols)));
		bldr.style(AttributedStyle.INVERSE_OFF);
		vp.write(bldr.toAnsi().getBytes(vp.getCharacterSet()));
		
	}

	private String getMountText() {
		return mounts.size() == 1 
				? MessageFormat.format(RESOURCES.getString("mount.active"), name) 
				: MessageFormat.format(RESOURCES.getString("mounts.active"), mounts.size());
	}
}