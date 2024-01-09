package com.sshtools.pretty.pricli;

import java.io.EOFException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Callable;

import com.sshtools.client.tasks.FileTransferProgress;
import com.sshtools.common.util.FileUtils;

import me.tongfei.progressbar.ConsoleProgressBarConsumer;
import me.tongfei.progressbar.InteractiveConsoleProgressBarConsumer;
import me.tongfei.progressbar.ProgressBar;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command
public abstract class LocalFileCommand implements Callable<Integer> {

	@ParentCommand
	protected RootCommand parent;

	public enum FilenameCompletionMode {
		DIRECTORIES_REMOTE, DIRECTORIES_REMOTE_THEN_LOCAL, DIRECTORIES_LOCAL, DIRECTORIES_LOCAL_THEN_REMOTE, REMOTE,
		REMOTE_THEN_LOCAL, LOCAL, LOCAL_THEN_REMOTE, NONE
	}

	private final FilenameCompletionMode mode;

	protected LocalFileCommand() {
		this(FilenameCompletionMode.NONE);
	}

	protected LocalFileCommand(FilenameCompletionMode mode) {
		this.mode = mode;
	}

	public final FilenameCompletionMode completionMode() {
		return mode;
	}

	public interface FileOp {
		void op(String path) throws Exception;
	}

	public interface PathOp {
		void op(Path path) throws Exception;
	}

	protected Path expandLocalSingle(Optional<Path> path) throws IOException {
		return expandLocalSingleOr(path).orElseGet(parent.cli()::cwd);
	}

	protected Optional<Path> expandLocalSingleOr(Optional<Path> path) throws IOException {
		if (path.isEmpty()) {
			return path;
		} else
			return Optional.of(expandLocalSingle(path.get()));
	}

	protected Path expandLocalSingle(Path path) throws IOException {
		var l = new ArrayList<Path>();
		expandLocalAndDo((fp) -> {
			if (!l.isEmpty())
				throw new EOFException();
			l.add(fp);
		}, true, path);
		if (l.isEmpty())
			return path;
		else
			return l.get(0);
	}

	protected Path[] expandLocalArray(Path[] paths) throws IOException {
		var l = new ArrayList<Path>();
		for (var path : paths) {
			expandLocalAndDo((fp) -> {
				l.add(fp);
			}, true, path);
		}
		return l.toArray(new Path[0]);
	}

	protected void expandLocalAndDo(PathOp op, boolean recurse, Path... paths) throws IOException {

		var lcwd = parent.cli().cwd();

		for (var path : paths) {

			path = expandSpecialLocalPath(path);
			path = path.normalize();

			if (path.toString().equals("..")) {
				var parentPath = lcwd.toAbsolutePath().getParent();
				path = parentPath == null ? path.getRoot() : parentPath;
			}

			if (Files.exists(path)) {
				try {
					op.op(path);
					continue;
				} catch (EOFException ee) {
					return;
				} catch (IOException | RuntimeException re) {
					throw re;
				} catch (Exception e) {
					throw new IOException("Failed to match pattern.", e);
				}
			}

			var root = path.isAbsolute() ? path.getRoot() : lcwd;
			var resolved = root;
			var pathCount = path.getNameCount();

			for (int i = 0; i < pathCount; i++) {

				var pathPart = path.getName(i);
				var matches = 0;

				try (var stream = Files.newDirectoryStream(resolved)) {

					var matcher = FileSystems.getDefault().getPathMatcher("glob:" + pathPart.toString());

					for (var pathPartPath : stream) {
						var fullPath = resolved;
						if (matcher.matches(pathPartPath.getFileName())
								|| pathPartPath.getFileName().toString().equals(pathPart.toString())) {
							fullPath = fullPath.resolve(pathPartPath.getFileName());
							matches++;
							if (i == pathCount - 1) {
								try {
									op.op(path.isAbsolute() ? pathPartPath.normalize()
											: root.relativize(pathPartPath).normalize());
								} catch (EOFException ee) {
									return;
								} catch (IOException | RuntimeException re) {
									throw re;
								} catch (Exception e) {
									throw new IOException("Failed to match pattern.", e);
								}
							}
						}
					}
				}

				if (!recurse || matches == 0)
					break;

				resolved = resolved.resolve(pathPart);
			}
		}
	}

	public static FileTransferProgress fileTransferProgress(ProgressBar progress,
			String messagePattern) {
		return new FileTransferProgress() {
			private String file;

			@Override
			public void started(long bytesTotal, String file) {
				this.file = FileUtils.getFilename(file);
				progress.setExtraMessage(MessageFormat.format(messagePattern, this.file));
			}

			@Override
			public boolean isCancelled() {
				return false; /* TODO */
			}

			@Override
			public void progressed(long bytesSoFar) {
				progress.stepTo(bytesSoFar);
			}
		};
	}

	static Path expandSpecialLocalPath(Path path) {
		if (path.toString().equals("~") || path.toString().startsWith("~/") || path.toString().startsWith("~\\")) {
			return Paths.get(System.getProperty("user.home") + path.toString().substring(1));
		} else
			return path;
	}

	protected ConsoleProgressBarConsumer createConsoleConsumer() {
		return new InteractiveConsoleProgressBarConsumer(new PrintStream(parent.cli().jline().output())) {
			@Override
			public int getMaxRenderedLength() {
				return parent.cli().terminal().getViewport().getColumns();
			}
		};
	}
}
