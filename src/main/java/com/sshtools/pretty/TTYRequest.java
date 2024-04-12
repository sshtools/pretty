package com.sshtools.pretty;

import java.util.Optional;

import com.sshtools.pretty.Shells.Shell;

public class TTYRequest {

	public final static class Builder {
		private Optional<Shell> shell = Optional.empty();
		
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
	
	private Optional<Shell> shell;

	private TTYRequest(Builder builder) {
		this.shell = builder.shell;
	}
	
	public Optional<Shell> shell() {
		return shell;
	}
}
