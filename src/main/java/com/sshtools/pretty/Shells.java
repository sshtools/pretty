package com.sshtools.pretty;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class Shells {

	public enum ShellType {
		BUILTIN, EXTERNAL
	}

	public static final String NATIVE = "native";
	
	public static record Shell(ShellType type, String commandName, Path path, String name, String version, String... args) { 
		
		public String toFullCommandText() {
			var b = new StringBuffer(commandName);
			if(args.length > 0) {
				b.append(' ');
				b.append(String.join(" ", args));
			}
			return b.toString();
		}

		public String[] fullCommand() {
			var arr = new String[(args == null ? 0 : args.length) + 1];
			arr[0] = path.toString();
			if(args != null)
				System.arraycopy(args, 0, arr, 1, args.length);
			return arr;
		}
	}
	
	private final List<Shell> shells = new ArrayList<>();
	
	public static void main(String[] args) {
		new Shells().shells.forEach(System.out::println);
	}
	
	public Shells() {
		checkShell("bash", "GNU Bourne-Again Shell", new String[] {"--version"}, ".*version ([^\s].*) .*", new String[] {"-l"}, "/usr/bin");
		checkShell("dash", "POSIX Shell", null, null, new String[] {"-l"}, "/usr/bin");
		checkShell("zsh", "The Z Shell", new String[] {"--version"}, ".* (.*) .*", new String[] {"-l"}, "/usr/bin");
		checkShell("csh", "C-like Shell", null, null, new String[] {"-l"}, "/usr/bin");
		checkShell("pwsh", "Microsoft Powershell",  new String[] {"--version"}, ".* (.*)", new String[] {"-Login"}, "/usr/bin");
		shells.add(new Shell(ShellType.BUILTIN, "serial", (Path)null, "Pretty Serial Support", null, new String[0]));
		shells.add(new Shell(ShellType.BUILTIN, "ssh", (Path)null, "Maverick Synergy SSH", null, new String[0]));
		shells.add(new Shell(ShellType.BUILTIN, NATIVE, (Path)null, "Default native shell", null, new String[0]));
	}
	
	public Optional<Shell> getDefault() {
		/* TODO best default for operating system, e.g. lookup /etc/passwd on Linux */
		return shells.isEmpty() ? Optional.empty() : Optional.of(shells.get(0));
	}

	public Optional<Shell> get(String name) {
		return shells.stream().filter(s -> s.name.equals(name)).findFirst();
	}

	public Optional<Shell> getByCommandName(String commandName) {
		return shells.stream().filter(s -> s.commandName.equals(commandName)).findFirst();
	}

	public List<Shell> getAll() {
		return Collections.unmodifiableList(shells);
	}
	
	private void checkShell(String commandName, String description, String[] versionArgs, String versionPattern, String[] loginShellArgs, String... paths) {
		var pvar = System.getenv("PATH");
		if(pvar != null && findInPaths(commandName, description, versionArgs, versionPattern, loginShellArgs, pvar.split(File.pathSeparator)))
			return;
		findInPaths(commandName, description, versionArgs, versionPattern, loginShellArgs, paths);
	}

	private boolean findInPaths(String commandName, String description, String[] versionArgs, String versionPattern,
			String[] loginShellArgs, String[] arr) {
		for(var a : arr) {
			Path cmd;
			if(System.getProperty("os.name").toLowerCase().contains("windows")) {
				cmd = Paths.get(a).resolve(commandName + ".exe");
			}
			else {
				cmd = Paths.get(a).resolve(commandName);
			}
			
			if(Files.exists(cmd)) {
				shells.add(new Shell(ShellType.EXTERNAL, commandName, cmd, description, getVersion(cmd, versionPattern, versionArgs), loginShellArgs));
				return true;
			}
		}
		return false;
	}

	private String getVersion(Path cmd, String pattern,  String... args) {
		if(pattern == null) {
			return null;
		}
		var fl = firstLineOfOutput(cmd, args);
		if(fl == null)
			return null;
		else {
			var ptn = Pattern.compile(pattern);
			var mtchr = ptn.matcher(fl);
			if(mtchr.matches()) {
				return mtchr.group(1);
			}
			else
				return null;
		}
	}
	
	private String firstLineOfOutput(Path cmd, String... args) {
		try {
			var pb = new ProcessBuilder(cmd.toString());
			pb.command().addAll(Arrays.asList(args));
			pb.redirectErrorStream(true);
			var prc = pb.start();
			try(var rdr = new BufferedReader(new InputStreamReader(prc.getInputStream()))) {
				return rdr.readLine();
			}
		}
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}
}
