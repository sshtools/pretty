package com.sshtools.pretty.pricli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.StreamSupport;

import com.sshtools.common.permissions.PermissionDeniedException;
import com.sshtools.common.sftp.SftpStatusException;
import com.sshtools.common.ssh.SshException;
import com.sshtools.common.util.Utils;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "lls", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "List directory")
public class Lls extends LocalFileCommand {

	static final String SftpLongnameDateFormat = "MMM dd  yyyy";
	static final String SftpLongnameDateFormatWithTime = "MMM dd HH:mm";

	@Option(names = "-l", description = "show files with the long name format")
	boolean longnames;

	@Option(names = "-a", description = "show hidden files")
	boolean showHidden;

	@Parameters(index = "0", arity = "0..1", paramLabel = "PATH", description = "path of directory to list")
	Optional<Path> path;

	public Lls() {
		super(FilenameCompletionMode.DIRECTORIES_LOCAL);
	}

	@Override
	public Integer call() throws Exception {

		if (longnames) {
			printLongnames();
		} else {
			printNames();
		}

		return 0;
	}

	private void printNames() throws IOException, SftpStatusException, SshException {

		var cli = parent.cli();
		var writer = cli.jline().writer();
		var maximumFilenameLength = 0;
		var columns = cli.terminal().getViewport().getColumns();

		try (var stream = Files.newDirectoryStream(expandLocalSingle(path))) {
			for (var p : stream) {
				String displayName = p.getFileName().toString();
				if (Utils.isBlank(displayName) || (displayName.startsWith(".") && !showHidden)) {
					continue;
				}
				maximumFilenameLength = Math.max(displayName.length() + 1, maximumFilenameLength);
			}
		}

		var printingColumns = 1;
		if (maximumFilenameLength < (columns / 2)) {
			printingColumns = columns / maximumFilenameLength;
		}

		if (printingColumns > 1) {
			var format = "%1$-" + (columns / printingColumns) + "s";

			try (var stream = Files.newDirectoryStream(expandLocalSingle(path))) {
				var itr = stream.iterator();
				while (itr.hasNext()) {
					for (int i = 0; i < printingColumns; i++) {
						writer.print(String.format(format, itr.next().getFileName()));
						if (!itr.hasNext()) {
							break;
						}
					}
					writer.println();
				}
			}
		} else {
			try (var stream = Files.newDirectoryStream(expandLocalSingle(path))) {
				for (var p : stream) {
					writer.println(p.getFileName().toString());
				}
			}
		}
	}

	private void print(Path file) {
		var writer = parent.cli().jline().writer();
		var basicAttrView = Files.getFileAttributeView(file, BasicFileAttributeView.class);
		try {
			var basicAttr = basicAttrView.readAttributes();
			var posixView = Files.getFileAttributeView(file, PosixFileAttributeView.class);
			if (posixView != null) {
				var posixAttr = posixView.readAttributes();

				writer.format("%9s %01d %-9s %-9s %10d %12s %s%n", toPermissionsString(posixAttr), "1",
						posixAttr.owner().getName(), posixAttr.group().getName(), posixAttr.size(),
						getModTimeStringInContext(Files.getLastModifiedTime(file), Locale.getDefault()),
						file.getFileName());
				writer.flush();
			} else {
				var winView = Files.getFileAttributeView(file, DosFileAttributeView.class);
				if (winView != null) {
					var winAttr = winView.readAttributes();
				} else {
				}
			}
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}

	}

	private Object toPermissionsString(PosixFileAttributes posixAttr) {
		// TODO Auto-generated method stub
		return null;
	}

	private String getModTimeStringInContext(FileTime mtime, Locale locale) {
		if (mtime == null) {
			return "";
		}

		SimpleDateFormat df;
		long mt = mtime.toMillis();
		long now = System.currentTimeMillis();

		if ((now - mt) > (6 * 30 * 24 * 60 * 60 * 1000L)) {
			df = new SimpleDateFormat(SftpLongnameDateFormat, locale);
		} else {
			df = new SimpleDateFormat(SftpLongnameDateFormatWithTime, locale);
		}

		return df.format(new Date(mt));
	}

	private void printLongnames() throws IOException {

		try (var stream = Files.newDirectoryStream(expandLocalSingle(path))) {
			StreamSupport.stream(stream.spliterator(), false).sorted(Comparator.comparing(Path::toString))
					.forEach(f -> {
						print(f);
					});
		}
	}

}
