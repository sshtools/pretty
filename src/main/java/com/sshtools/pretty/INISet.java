package com.sshtools.pretty;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.install4j.api.Util;
import com.sshtools.jini.INI;
import com.sshtools.jini.schema.INISchema;

public final class INISet {

	private static final String DEFAULT_APP_NAME = "pretty";

	public enum Scope {
		GLOBAL, USER
	}

	public final static class Builder {
		private Optional<INISchema> schema = Optional.empty();
		private Optional<INI> defaultIni = Optional.empty();
		private Optional<String> app = Optional.empty();
		private Map<Scope, Path> paths = new HashMap<>();

		private final String name;
		
		public Builder(String name) {
			this.name = name;
		}

		public Builder withApp(Class<?> app) {
			return withApp(app.getName());
		}

		public Builder withApp(String app) {
			this.app = Optional.of(app);
			return this;
		}
		
		public Builder withSchema(Class<?> base, String resource) {
			try(var in = base.getResourceAsStream(resource)) {
				return withSchema(in);
			}
			catch(IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		}
		
		public Builder withDefault(Class<?> base, String resource) {
			try(var in = base.getResourceAsStream(resource)) {
				return withDefault(in);
			}
			catch(IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		}
		
		public Builder withSchema(Path path) {
			return withSchema(INISchema.fromFile(path));
		}
		
		public Builder withSchema(InputStream in) {
			return withSchema(INISchema.fromInput(in));
		}
		
		public Builder withSchema(INISchema schema) {
			this.schema = Optional.of(schema);
			return this;
		}
		
		public Builder withDefault(InputStream in) {
			return withDefault(INI.fromInput(in));
		}
		
		public Builder withDefault(INI defaultIni) {
			this.defaultIni = Optional.of(defaultIni);
			return this;
		}
		
		public Builder withPath(Scope scope, Path path) {
			paths.put(scope, path);
			return this;
		}
		
		public INISet build() {
			return new INISet(this);
		}
		
	}
	private final Optional<INISchema> schema;
	private final Optional<INI> defaultIni;
	private final String app;
	private final Map<Scope, Path> paths;

	private final String name;
	
	private INISet(Builder builder) {
		this.schema = builder.schema;
		this.defaultIni = builder.defaultIni;
		this.app = builder.app.orElse(DEFAULT_APP_NAME);
		this.paths = Collections.unmodifiableMap(new HashMap<>(builder.paths));
		this.name = builder.name;
	}
	
	private void load() {
		load(Scope.GLOBAL);
	}

	private void load(Scope scope) {
		var path = appPathForScope(scope);
		if(Files.exists(path)) {
			/* First look for <name>.ini */
			var setRootPath = path.resolve(name + ".ini");
			if(Files.exists(setRootPath)) {
				var setRootIni = INI.fromFile(setRootPath);
//				setRootIni.
			}
		}
	}
	
	private Path appPathForScope(Scope scope) {
		var root = rootPathForScope(scope);
		if(!Util.isLinux() && !Util.isWindows() && scope == Scope.USER) {
			return root.resolve("." + app);
		}
		else {
			return root.resolve(app);
		}
	}
	
	private Path rootPathForScope(Scope scope) {
		var root = paths.get(scope);
		if(root == null) {
			if (Util.isLinux()) {
				switch (scope) {
				case GLOBAL:
					return Paths.get("/etc");
				case USER:
					return resolveHome().resolve(".config");
				default:
					throw new UnsupportedOperationException();
				}

			} else if (Util.isWindows()) {
				switch (scope) {
				case GLOBAL:
					return Paths.get("C:\\Program Files\\Common Files");
				case USER:
					return resolveHome().resolve("AppData").resolve("Roaming");
				default:
					throw new UnsupportedOperationException();
				}
			} else {
				switch (scope) {
				case GLOBAL:
					return Paths.get("/etc");
				case USER:
					return resolveHome();
				default:
					throw new UnsupportedOperationException();
				}
			}
		}
		return root;
	}

	private Path resolveHome() {
		return Paths.get(System.getProperty("user.home"));
	}
}
