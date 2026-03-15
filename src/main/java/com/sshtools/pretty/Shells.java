package com.sshtools.pretty;

import static com.sshtools.jajafx.FXUtil.maybeQueue;
import static com.sshtools.jini.INI.merge;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.install4j.api.Util;
import com.pty4j.Platform;
import com.sshtools.jini.Data;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.config.INISet;
import com.sshtools.jini.config.Monitor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.StringConverter;

public class Shells extends AbstractINISetSystem {

	private final static Logger LOG = LoggerFactory.getLogger(Shells.class);
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Shells.class.getName());

	public enum ShellType {
		BUILTIN, EXTERNAL
	}

	public static final String NATIVE = "native";
	public static final String PRICLI = "pricli";
	public static final String WINDOWS_DEFAULT = "cmd";
	public static final String MAC_OS_DEFAULT = "zsh";
	public static final String FALLBACK_DEFAULT = "zsh";
	public static final String UNIX_DEFAULT = "bash";
	
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
	
	private final Map<String, Shell> shells = new LinkedHashMap<>();
	private final ObservableList<Shell> shellList = FXCollections.observableArrayList();
	
	public Shells(AppContext app, Monitor monitor) {
		super(app, "shells.d");
		
		addOrUpdateShell(new Shell(ShellType.BUILTIN, "serial", "serial", (Path)null, null, "Pretty Serial Support", null, false, "connect"));
		addOrUpdateShell(new Shell(ShellType.BUILTIN, "ssh", "ssh", (Path)null, null, "Maverick Synergy SSH", null, false, "--prompt"));
		addOrUpdateShell(new Shell(ShellType.BUILTIN, PRICLI, PRICLI, (Path)null, null, "Built-in shell", null, false));
		addOrUpdateShell(new Shell(ShellType.BUILTIN, NATIVE, NATIVE, (Path)null, null, "Default native shell", null, false));
		
		loadShellsFromIni(new INISet.Builder("shells").
				withMonitor(monitor).
				withDefault(Shells.class, "Shells.ini").
				build().document());
	}
	
	private void addOrUpdateShell(Shell shell) {
		var was = shells.put(shell.id, shell);
		if(was != null) {
			shellList.remove(was);
			if(shell.path == null)
				LOG.info("Updating shell '{}' [{}]", shell.id(), shell.name());
			else
				LOG.info("Updating shell '{}' [{}] @ {}", shell.id(), shell.name(), shell.path());
		}
		else {
			if(shell.path == null)
				LOG.info("Adding shell '{}' [{}]", shell.id(), shell.name());
			else
				LOG.info("Adding shell '{}' [{}] @ {}", shell.id(), shell.name(), shell.path());
		}
		shellList.add(shell);
	}

	private void loadShellsFromIni(Data ini) {
		Arrays.asList(ini.allSections()).forEach(sec -> {
			try {
				if(isForOs(sec)) {
					var shl = shellFromSection(sec);
					maybeQueue(() ->addOrUpdateShell(shl));
				}
			}  catch (Exception e) {
				LOG.debug("Failed to load shell.", e);
			}
		});
		ini.onValueUpdate(vu -> {
			if(vu.parent() instanceof Section sec) {
				var shl = shellFromSection(sec);
				maybeQueue(() ->addOrUpdateShell(shl));
			}
		});
		ini.onSectionUpdate(su -> {
			switch(su.type()) {
			case REMOVE:
				var shl = shells.remove(su.section().key());
				if(shl != null) {
					LOG.info("Removing shell '{}' [{}]", shl.id(), shl.name());
					maybeQueue(() -> shellList.remove(shl));
				}
				break;
			case ADD:
				addOrUpdateShell(shellFromSection(su.section()));
				break;
			default:
				/* Ignore section updates, we may get partial sections */
				/* TODO fix this in jini? */
				break;
			}
		});
		
	}
	
	private Shell shellFromSection(Section section) {
		var cmd = section.getOr("command").orElse(section.key());
		var name = section.get("name", section.key());
		
		var paths = merge(
			section.getAllElse("paths"),
			section.getAllElse("path")
		);

		var searchPaths = section.getBoolean("search-path", paths.length == 0);
		var path = locate(searchPaths, cmd, paths);
		
		var version = getVersion(path, section.get("version-pattern", null), merge(
				section.getAllElse("version-argument"),
				section.getAllElse("version-arguments")
			)
		);
		
		var arguments = merge(
				section.getAllElse("argument"),
				section.getAllElse("arguments")
		);
		
		return new Shell(
			ShellType.EXTERNAL,
			section.key(),
			cmd,
			path,
			merge(
				section.getAllElse("login-shell-argument"),
				section.getAllElse("login-shell-arguments")
			),
			name,
			version,
			section.getBoolean("cygwin", false),
			arguments
		);
	}
	
	private Path locate(boolean searchPath, String cmd, String... paths) {
		if(paths.length > 0) {
			var fnd = find(cmd, paths);
			if(fnd.isPresent())
				return fnd.get();
		}
		if(searchPath) {
			var pvar = System.getenv("PATH");
			if(pvar == null)
				throw new IllegalStateException("No PATH variable is set."); 
			var fnd = find(cmd, pvar.split(File.pathSeparator));
			if(fnd.isPresent())
				return fnd.get();
		}

		throw new IllegalStateException(MessageFormat.format("No command for the shell ''{0}'' could be found.", cmd));
	}

	public Optional<Shell> getDefault() {
		if(shells.isEmpty()) {
			return Optional.empty();
		}
		else {
			var shl = System.getenv("SHELL");
			if(shl != null) {
				try {
					var defShl = getByCommandPath(Paths.get(shl).toRealPath());
					if(defShl.isPresent())
						return defShl;
				}
				catch(IOException ioe) {
				}
			}
		}
		
		if(Platform.isWindows()) {
			var defShl = getById(WINDOWS_DEFAULT);
			if(defShl.isPresent())
				return defShl;
		}
		else if(Platform.isMac()) {
			var defShl = getById(WINDOWS_DEFAULT);
			if(defShl.isPresent())
				return defShl;
		}
		else if(Platform.isLinux()) {
			var defShl = getById(UNIX_DEFAULT);
			if(defShl.isPresent())
				return defShl;
		}
		else {
			var defShl = getById(FALLBACK_DEFAULT);
			if(defShl.isPresent())
				return defShl;
		}
		
		/* Fallback, will always exist */
		return Optional.of(shells.get(PRICLI));
	}

	public Optional<Shell> getByCommandPath(Path path) {
		return shells.values().stream().filter(s -> {
			try {
				return s.path != null && path.equals(s.path.toRealPath());
			} catch (IOException e) {
				return false;
			}
		}).findFirst();
	}

	public Optional<Shell> getByCommandName(String commandName) {
		return shells.values().stream().filter(s -> s.commandName.equals(commandName)).findFirst();
	}

	public Optional<Shell> getById(String id) {
		return Optional.ofNullable(shells.get(id));
	}

	public ObservableList<Shell> getAll() {
		return shellList;
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
	
	private Optional<Path> find(String commandName, String... arr) {
		for(var a : arr) {
			Path cmd;
			if(Util.isWindows()) {
				cmd = Paths.get(a).resolve(commandName + ".exe");
				if(!Files.exists(cmd)) {
					cmd = Paths.get(a).resolve(commandName + ".cmd");
				}
			}
			else {
				cmd = Paths.get(a).resolve(commandName);
			}
			
			if(Files.exists(cmd)) {
				return Optional.of(cmd);
			}
		}
		return Optional.empty();
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
