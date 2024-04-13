package com.sshtools.pretty;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

import javafx.util.StringConverter;

public class Shells {

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Shells.class.getName());

	public enum ShellType {
		BUILTIN, EXTERNAL
	}

	public static final String NATIVE = "native";
	public static final String PRICLI = "pricli";
	
	public static record Shell(ShellType type, String id, String commandName, Path path, String[] loginShellArgs, String name, String version, boolean cygwin, String... args) { 
		
		public String toFullCommandText(boolean loginShell) {
			var b = new StringBuffer(commandName);
			if(loginShell && loginShellArgs != null) {
				b.append(' ');
				b.append(String.join(" ", loginShellArgs));
			}
			if(this.args != null && args.length > 0) {
				b.append(' ');
				b.append(String.join(" ", args));
			}
			return b.toString();
		}

		public String[] fullCommand(boolean loginShell) {
			var l = new ArrayList<>();
			l.add(path.toString());
			if(loginShell && loginShellArgs != null) {
				l.addAll(Arrays.asList(loginShellArgs));
			}
			if(args != null)
				l.addAll(Arrays.asList(args));
			return l.toArray(new String[0]);
		}

		public static Shell forCommand(String id) {
			var parsedCommand = Strings.parseQuotedString(id);
			var cmdName = parsedCommand.remove(0).trim();
			return new Shell(ShellType.EXTERNAL, "cmd", cmdName, Paths.get(cmdName), null, cmdName, id, false, parsedCommand.toArray(new String[0]));
			
		}
	}
	
	private final List<Shell> shells = new ArrayList<>();
	
	public static void main(String[] args) {
		new Shells().shells.forEach(System.out::println);
	}
	
	public Shells() {
		String sysroot = System.getenv("SystemRoot");
		// String id, String commandName, boolean cygwin, String description, String[] args, String[] versionArgs, String[] loginShellArgs, String versionPattern, String... paths
		if(sysroot != null) {
			checkShell("cmd", "cmd", false, "DOS Command Prompt", null, null, null, null, "C:\\Windows\\System32");
			checkShell("powershell", "powershell", false, "Microsoft Powershell",  null, null, null, null, sysroot + File.separator + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe");
			checkPosixShells();
			checkShell("msys2", "msys2_shell", false, "MSys2", new String[] { "-defterm", "-here", "-no-start", "-shell", "bash"}, null, null, null, "C:\\msys64");
			checkShell("mingw64", "msys2_shell", false, "Mingw64 MSys2 Profile", new String[] { "-defterm", "-here", "-no-start", "-mingw64", "-shell", "bash"}, null, null, null, "C:\\msys64");
			checkShell("mingw32", "msys2_shell", false, "Mingw32 MSys2 Profile", new String[] { "-defterm", "-here", "-no-start", "-mingw32", "-shell", "bash"}, null, null, null, "C:\\msys64");
			checkShell("clang64", "msys2_shell", false, "Clang64 MSys2 Profile", new String[] { "-defterm", "-here", "-no-start", "-clang64", "-shell", "bash"}, null, null, null, "C:\\msys64");
			checkShell("clang32", "msys2_shell", false, "Clang32 MSys2 Profile", new String[] { "-defterm", "-here", "-no-start", "-clang32", "-shell", "bash"}, null, null, null, "C:\\msys64");
			checkShell("clangarm64", "msys2_shell", false, "ClangArm64 MSys2 Profile", new String[] { "-defterm", "-here", "-no-start", "-clangarm64", "-shell", "bash"}, null, null, null, "C:\\msys64");
			checkShell("cygwin", "bash", true, "Cygwin Bash", null, new String[] {"--version"}, new String[] {"-l"}, ".*version ([^\s].*) .*", "C:\\cygwin64\\bin");
		}
		else {
			checkPosixShells();
			checkShell("powershell", "pwsh", false, "Microsoft Powershell",  null, new String[] {"--version"}, new String[] {"-Login"}, ".* (.*)", null, "/usr/bin");
		}
		shells.add(new Shell(ShellType.BUILTIN, "serial", "serial", (Path)null, null, "Pretty Serial Support", null, false, new String[0]));
		shells.add(new Shell(ShellType.BUILTIN, "ssh", "ssh", (Path)null, null, "Maverick Synergy SSH", null, false, "--prompt"));
		shells.add(new Shell(ShellType.BUILTIN, PRICLI, PRICLI, (Path)null, null, "Built-in shell", null, false));
		shells.add(new Shell(ShellType.BUILTIN, NATIVE, NATIVE, (Path)null, null, "Default native shell", null, false));
	}

	private void checkPosixShells() {
		checkShell("bash", "bash", false, "GNU Bourne-Again Shell", null, new String[] {"--version"}, new String[] {"-l"}, ".*version ([^\s].*) .*", "/usr/bin");
		checkShell("dash", "dash", false, "POSIX Shell", null, null, new String[] {"-l"}, null, "/usr/bin");
		checkShell("zsh", "zsh", false, "The Z Shell", null, new String[] {"--version"}, new String[] {"-l"}, ".* (.*) .*",  "/usr/bin");
		checkShell("csh", "csh", false, "C-like Shell", null, null, new String[] {"-l"}, null, "/usr/bin");
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

	public Optional<Shell> getById(String id) {
		return shells.stream().filter(s -> s.id.equals(id)).findFirst();
	}

	public List<Shell> getAll() {
		return Collections.unmodifiableList(shells);
	}

	public static String toDisplayName(Shell object) {
		if(object == null)
			return RESOURCES.getString("customCommand");
		else {
			if(object.version() == null)
				return MessageFormat.format(RESOURCES.getString("shellItemNoVersion"), object.name(), object.id());
			else
				return MessageFormat.format(RESOURCES.getString("shellItem"), object.name(), object.id(), object.version());
		}
	}
	
	private void checkShell(String id, String commandName, boolean cygwin, String description, String[] args, String[] versionArgs, String[] loginShellArgs, String versionPattern, String... paths) {
		findInPaths(id,commandName, description, args, versionArgs, versionPattern, loginShellArgs, paths, cygwin);
		if(paths.length == 0) {
			var pvar = System.getenv("PATH");
			if(pvar != null && findInPaths(id, commandName, description, args, versionArgs, versionPattern, loginShellArgs, pvar.split(File.pathSeparator), cygwin))
				return;
		}
	}

	private boolean findInPaths(String id, String commandName, String description, String[] args, String[] versionArgs, String versionPattern,
			String[] loginShellArgs, String[] arr, boolean cygwin) {
		for(var a : arr) {
			Path cmd;
			if(System.getProperty("os.name").toLowerCase().contains("windows")) {
				cmd = Paths.get(a).resolve(commandName + ".exe");
				if(!Files.exists(cmd)) {
					cmd = Paths.get(a).resolve(commandName + ".cmd");
				}
			}
			else {
				cmd = Paths.get(a).resolve(commandName);
			}
			
			if(Files.exists(cmd)) {
				shells.add(new Shell(ShellType.EXTERNAL, id, commandName, cmd, loginShellArgs, description, getVersion(cmd, versionPattern, versionArgs), cygwin, args));
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
			if(args != null)
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

	public static StringConverter<Shell> stringConverter() {
		return new StringConverter<Shell>() {
			@Override
			public String toString(Shell object) {
				return toDisplayName(object);
			}

			@Override
			public Shell fromString(String string) {
				return null;
			}
		};
	}
}
