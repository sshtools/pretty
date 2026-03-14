package com.sshtools.pretty.pricli;

import com.sshtools.pretty.TTY;

public interface RootCommand {
	PricliShell cli();

	TTY tty();
}
