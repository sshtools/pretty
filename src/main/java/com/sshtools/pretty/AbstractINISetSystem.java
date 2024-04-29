package com.sshtools.pretty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.install4j.api.Util;
import com.sshtools.jini.INI.Section;

public class AbstractINISetSystem {
	
	protected final AppContext app;
	private final String dropInName;
	
	protected AbstractINISetSystem(AppContext app, String dropInName) {
		this.app = app;
		this.dropInName = dropInName;
	}

	public final Path getDropInPath() {
		var dir = app.getConfiguration().dir().resolve(dropInName);
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return dir;
	}
	
	protected boolean isForOs(Section section) {
		var oss = section.getAllElse("os");
		if(oss.length == 0) {
			return true;
		}
		var osName = getOs();
		var expect = true;
		for(var os : oss) {			
			if(os.startsWith("!")) {
				expect = false;
				os = os.substring(1);
			}
			else {
				expect = true;
			}
			os = os.toLowerCase();
			if(expect != osName.equals(os)) {
				return false;
			}
		}
		return true;
	}
	
	protected String getOs() {
		if(Util.isWindows()) {
			return "windows";
		}
		else if(Util.isMacOS()) {
			return "macos";
		}
		else if(Util.isLinux()) {
			return "linux";
		}
		else {
			return "other";
		}
	}
}
