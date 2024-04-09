package com.sshtools.pretty;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.ResourceBundle;

import org.slf4j.event.Level;

import com.sshtools.jajafx.AppUpdateService;
import com.sshtools.jajafx.DelegatingAppUpdateService;
import com.sshtools.jajafx.JajaApp;
import com.sshtools.jaul.AppCategory;
import com.sshtools.jaul.ArtifactVersion;
import com.sshtools.jaul.JaulApp;
import com.sshtools.jaul.Phase;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;
import uk.co.bithatch.nativeimage.annotations.Bundle;

@Command(name = "pretty", mixinStandardHelpOptions = true, description = "A Terminal Emulator", versionProvider = Pretty.Version.class)
@JaulApp(id = Pretty.TOOLBOX_APP_ID, category = AppCategory.GUI, updaterId = "54", updatesUrl = "https://sshtools-public.s3.eu-west-1.amazonaws.com/pretty/${phase}/updates.xml")
@Bundle
public class Pretty extends JajaApp<PrettyApp, PrettyAppWindow> {

	private final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Pretty.class.getName());

	@Option(names = { "-C", "--no-close" }, description = "Do not allow the window to be closed manually.")
	private boolean noClose;

	@Option(names = { "-M", "--no-minimize" }, description = "Do not allow the window to be minimized manually.")
	private boolean noMinimize;

	@Option(names = { "-R", "--no-resize" }, description = "Do not allow the window to be resized.")
	private boolean noResize;

	@Option(names = { "-D",
			"--no-drag" }, description = "Do not allow the window to be moved. Will be centred on primary monitor.")
	private boolean noMove;

	@Option(names = { "-s", "--size" }, description = "Size of window, in the format <width>X<height>.")
	private String size;

	@Option(names = { "-T", "--always-on-top" }, description = "Keep the window on top of others.")
	private boolean alwaysOnTop;

	@Option(names = { "-U", "--no-updates" }, description = "Do not perform any updates.")
	private boolean noUpdates;

	@Option(names = { "-L", "--log-level" }, description = "Logging level.")
	private Optional<Level> logLevel;

	@Option(names = { "-c", "--cwd" }, description = "Working directory for local shells.")
	private Optional<Path> workingDirectory;

	private final Configuration configuration;

	public final static class PrettyBuilder extends JajaAppBuilder<Pretty, PrettyBuilder, PrettyApp> {

		public static PrettyBuilder create() {
			return new PrettyBuilder();
		}

		private PrettyBuilder() {
		}

		@Override
		public Pretty build() {
			return new Pretty(this);
		}
	}

	public final static class Version implements IVersionProvider {

		@Override
		public String[] getVersion() throws Exception {
			return Pretty.getVersion();
		}
	}

	public final static String TOOLBOX_APP_ID = "com.sshtools.Pretty";

	public static void main(String[] args) {
		var bldr = PrettyBuilder.create().withInceptionYear(2023).withApp(PrettyApp.class)
				.withAppResources(Pretty.RESOURCES);
		System.exit(new CommandLine(bldr.build()).execute(args));
	}

	Pretty(PrettyBuilder builder) {
		super(builder);
		configuration  =new Configuration(Paths.get(System.getProperty("user.home"), ".pretty"));
	}

	@Override
	protected AppUpdateService createDefaultUpdateService() {
		return new DelegatingAppUpdateService(super.createDefaultUpdateService()) {

			@Override
			public Phase[] getPhases() {
				return Phase.values();
			}

			@Override
			public boolean isUpdatesEnabled() {
				return true;
			}
		};
	}
	
	public Path getDefaultWorkingDirectory() {
		return workingDirectory.orElseGet(() -> Paths.get(System.getProperty("user.dir")));
	}
	
	public Configuration getConfiguration() {
		return configuration;
	}

	public boolean isNoUpdates() {
		return noUpdates;
	}

	public boolean isNoResize() {
		return noResize;
	}

	public boolean isAlwaysOnTop() {
		return alwaysOnTop;
	}

	public boolean isNoMove() {
		return noMove;
	}

	public String getSize() {
		return size;
	}

	public boolean isNoClose() {
		return noClose;
	}

	public boolean isNoMinimize() {
		return noMinimize;
	}
	
	public static String[] getVersion() {
		return new String[] { ArtifactVersion.getVersion("com.sshtools", "pretty") };		
	}
	
	@Override
	protected void initCall() throws Exception {
		logLevel.ifPresent(l -> System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", l.name()));
	}

}
