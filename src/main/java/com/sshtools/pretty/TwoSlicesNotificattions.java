package com.sshtools.pretty;

import java.util.Optional;
import java.util.ResourceBundle;

import com.sshtools.terminal.emulation.TerminalNotifications;
import com.sshtools.twoslices.ToastBuilder;
import com.sshtools.twoslices.ToastType;

public class TwoSlicesNotificattions implements TerminalNotifications {

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(TwoSlicesNotificattions.class.getName());
	@Override
	public void notify(String title, Optional<String> body, Optional<Runnable> action) {
		var bldr = new ToastBuilder().
			 type(ToastType.INFO).
			 title(body.isEmpty() || title == null ? "Pretty" : title);
		
		action.ifPresent(a -> bldr.action("Default", a::run));
		body.ifPresentOrElse(bldr::content, () -> bldr.content(title));
		bldr.toast();
	}

}
