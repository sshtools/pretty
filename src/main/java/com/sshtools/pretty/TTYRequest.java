package com.sshtools.pretty;

import java.util.Optional;

import com.sshtools.pretty.Shells.Shell;

public class TTYRequest {

	public final static class Builder {
		private Optional<Shell> shell = Optional.empty();
		private Optional<String> name = Optional.empty();
		
		public Builder withNamee(String name) {
			this.name = Optional.of(name);
			return this;
		}
		
		public Builder withCommand(String command) {
			return withShell(Shell.forCommand(command));
		}

		public Builder withShell(Shell shell) {
			this.shell = Optional.of(shell);
			return this;
		}

		public TTYRequest build() {
			return new TTYRequest(this);
		}
	}
	
	private final Optional<Shell> shell;
	private final String name;

	private TTYRequest(Builder builder) {
		this.shell = builder.shell;
		this.name = builder.name.orElseGet(() -> shell.map(Shell::name).orElse("TTY"));
	}
	
	public String name() {
		return name;
	}
	
	public Optional<Shell> shell() {
		return shell;
	}
}
