package com.sshtools.pretty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.sshtools.pretty.Shells.Shell;

public final class TTYRequest {

	public final static class Builder {
		private Optional<Shell> shell = Optional.empty();
		private Optional<String> name = Optional.empty();
		private List<String> script = new ArrayList<>();
		
		public Builder withName(String name) {
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

		public Builder withScript(List<String> script) {
			this.script.clear();
			this.script.addAll(script);
			return this;
		}

		public TTYRequest build() {
			return new TTYRequest(this);
		}
	}
	
	private final Optional<Shell> shell;
	private final String name;
	private final List<String> script;

	private TTYRequest(Builder builder) {
		this.shell = builder.shell;
		this.name = builder.name.orElseGet(() -> shell.map(Shell::name).orElse("TTY"));
		this.script = Collections.unmodifiableList(new ArrayList<>(builder.script));
	}
	
	public List<String> script() {
		return script;
	}
	
	public String name() {
		return name;
	}
	
	public Optional<Shell> shell() {
		return shell;
	}
}
