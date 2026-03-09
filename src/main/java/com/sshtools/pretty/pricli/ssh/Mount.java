package com.sshtools.pretty.pricli.ssh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.TimeoutException;

import org.cryptomator.jfuse.api.Fuse;
import org.cryptomator.jfuse.api.FuseMountFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.pretty.Configuration;
import com.sshtools.pretty.Constants;
import com.sshtools.pretty.pricli.Styling;
import com.sshtools.pretty.ssh.SFTPFileSystem;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "mount", aliases = { "mnt",
		"mn" }, usageHelpAutoWidth = true, description = "Mount remote directory to local filesystem")
public class Mount extends SftpCommand {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Mount.class.getName());

	private final static Logger LOG = LoggerFactory.getLogger(Mount.class);

	@Option(names = { "--libpath" }, description = "Path to libfuse3 shared library")
	private Optional<Path> libpath;

	@Parameters(index = "0", arity = "1", description = "Name of local mount")
	private String name;

	@Parameters(index = "1", arity = "0..1", description = "Optional remote path (/ will be used otherwise)")
	private String path;

	public Mount() {
		super(FilenameCompletionMode.LOCAL_THEN_REMOTE);
	}

	@Override
	public Integer call() throws Exception {
		var sftp = sftpClient();
		var configuration = parent.cli().tty().ttyContext().getContainer().getConfiguration();
		var ssh = sshClient();

		if (ActiveMount.mounts.containsKey(name)) {
			throw new IllegalStateException(
					MessageFormat.format(RESOURCES.getString("alreadyMounted"), name, ActiveMount.mounts.get(name).mountPoint()));
		}

		for (var entry : ActiveMount.mounts.entrySet()) {
			if (entry.getValue().fs().mount().equals(sftp)) {
				throw new IllegalStateException(MessageFormat.format(RESOURCES.getString("connectionMounted"), name,
						entry.getValue().mountPoint()));
			}
		}

		var builder = Fuse.builder();
		libpath.ifPresentOrElse(p -> builder.setLibraryPath(p.toString()), () -> {
			if (System.getProperty("os.name", "").toLowerCase().contains("linux")) {
				if (System.getProperty("os.arch", "amd64").equals("amd64")) {
					var path = Paths.get("/lib/x86_64-linux-gnu/libfuse3.so.3");
					if (Files.exists(path)) {
						builder.setLibraryPath(path.toString());
					}
				}
			}
		});

		var fuseOps = new SFTPFileSystem(path, sftp, builder.errno());
		var fuse = builder.build(fuseOps);
		var mountsDir = Paths
				.get(Configuration.expandSpecialLocalPath(configuration.transfers().get(Constants.MOUNT_PATH)));
		var mountPoint = mountsDir.resolve(name);

		LOG.info("Locally Mounting {} at {}...", name, mountPoint);
		try {
			if (!Files.exists(mountPoint)) {
				Files.createDirectories(mountPoint);
			}

			fuse.mount(name, mountPoint, "-s");
			LOG.info("Locally Mounted {} at {}...", name, mountPoint);
			var shutdownHook = new Thread(() -> {
				LOG.info("Shutting down.");
				try {
					fuse.close();
				} catch (TimeoutException e) {
				}
			});

			Runtime.getRuntime().addShutdownHook(shutdownHook);

			var mnt = new ActiveMount(name, parent.cli().tty(), fuseOps, ssh, fuse, shutdownHook, mountPoint);
			parent.cli().tty().status().add(mnt);
			ssh.getConnection().addEventListener(mnt);
			ActiveMount.mounts.put(name, mnt);

			parent.cli().result(Styling.styled(MessageFormat.format(RESOURCES.getString("mounted"), name, mountPoint))
					.toAttributedString());

		} catch (IllegalArgumentException | FuseMountFailedException | IOException e) {
			try {
				fuse.close();
			} catch (TimeoutException e1) {
			}
			throw new IOException(
					MessageFormat.format(RESOURCES.getString("failedToMount"), name, mountPoint, e.getMessage()), e);
		}
		return 0;
	}

}
