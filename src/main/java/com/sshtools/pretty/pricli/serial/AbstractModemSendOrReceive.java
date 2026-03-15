package com.sshtools.pretty.pricli.serial;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.pretty.Constants;
import com.sshtools.pretty.GUI;
import com.sshtools.pretty.Strings;
import com.sshtools.pretty.pricli.LocalFileCommand;
import com.sshtools.terminal.xyzmodem.XYZTransferProgress;

import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import me.tongfei.progressbar.ProgressBar;
import picocli.CommandLine.Option;

public abstract class AbstractModemSendOrReceive  extends LocalFileCommand {
	private final static Logger LOG  = LoggerFactory.getLogger(AbstractModemSendOrReceive.class);

	@Option(names = {"-n", "--no-prompt"}, description = "Do not prompt for confirmation before sending file")
	private boolean noPrompt;
	
	@Option(names = {"-d", "--debug"}, hidden = true, description = "Send debug information to the console")
	protected boolean debug;
	
	private ResourceBundle resources;

	private DirectoryChooser recvDirectoryChooser;

	protected static FileChooser sendChooser;
	
	protected AbstractModemSendOrReceive(ResourceBundle resources) {
		super(FilenameCompletionMode.LOCAL);
		this.resources = resources;
	}
	
	protected final void prompt() {
		if(!noPrompt) {
			parent.cli().reader().readLine(
					resources.getString("confirm"),
					(Character) null, null);
		}
	}
	
	protected final Path openSinglePaths() {
		return openMultiplePaths(true).getFirst();
	}
	
	protected final List<Path> openMultiplePaths() {
		return openMultiplePaths(true);
	}
	
	protected final Path saveDirectory() {
		return GUI.runAndBlock(() -> {
			if(recvDirectoryChooser == null) {
				recvDirectoryChooser = new DirectoryChooser();
				recvDirectoryChooser.setTitle(resources.getString("receiveDirectory"));
				recvDirectoryChooser.setInitialDirectory(Strings.parseFilePath(parent.tty().ttyContext().getContainer().getConfiguration().transfers().get(Constants.DOWNLOADS_KEY)).toFile());
			}
			var scene = parent.cli().terminal().getControl().getParent().getScene();
			var window = scene == null ? null : scene.getWindow();
			var selected = recvDirectoryChooser.showDialog(window);
			if(selected != null) {
				return selected.toPath();
			}
			else {
				throw new IllegalStateException(resources.getString("noDirectorySelected"));
			}				
		});
	}
	
	protected final Path saveSinglePath() {
		if(sendChooser == null) {
			sendChooser = new FileChooser();
			sendChooser.setInitialDirectory(Strings.parseFilePath(parent.tty().ttyContext().getContainer().getConfiguration().transfers().get(Constants.DOWNLOADS_KEY)).toFile());
		}
		sendChooser.setTitle(resources.getString("receive"));
		return GUI.runAndBlock(() -> {
			var scene = parent.cli().terminal().getControl().getParent().getScene();
			var window = scene == null ? null : scene.getWindow();
			var selected = sendChooser.showSaveDialog(window);
			if(selected != null) {
				return selected.toPath();
			}
			else {
				throw new IllegalStateException(resources.getString("noFileSelected"));
			}				
		});
	}
	
	protected final List<Path> openMultiplePaths(boolean multiple) {
		return GUI.runAndBlock(() -> {
			if(sendChooser == null) {
				sendChooser = new FileChooser();
				sendChooser.setInitialDirectory(Strings.parseFilePath(parent.tty().ttyContext().getContainer().getConfiguration().transfers().get(Constants.DOWNLOADS_KEY)).toFile());
			}
			sendChooser.setTitle(resources.getString("send"));
			var scene = parent.cli().terminal().getControl().getParent().getScene();
			var window = scene == null ? null : scene.getWindow();
			if(multiple) {
				var selected = sendChooser.showOpenMultipleDialog(window);
				if(selected != null && !selected.isEmpty()) {
					return selected.stream().map(File::toPath).toList();
				}
				else {
					throw new IllegalStateException(resources.getString("noFileSelected"));
				}
			}
			else {
				var selected = sendChooser.showOpenDialog(window);
				if(selected != null) {
					return List.of(selected.toPath());
				}
				else {
					throw new IllegalStateException(resources.getString("noFileSelected"));
				}				
			}
		});
	}

	protected XYZTransferProgress multiXYZTransferProgress() {
		return new XYZTransferProgress() {

			private ProgressBar pb;
			private XYZTransferProgress delegate;

			@Override
			public void message(String message) {
				delegate.message(message);
			}

			@Override
			public void start(String name, long size) {
				pb = progressBarBuilder(name, "KiB", 1024).build();
				delegate = xyzTransferListener(pb);
				delegate.start(name, size);
			}

			@Override
			public void progress(long transferred) {
				delegate.progress(transferred);
			}

			@Override
			public void done(Exception error) {
				pb.close();
			}
		};
	}

	protected static XYZTransferProgress xyzTransferListener(ProgressBar progress) {
		return new XYZTransferProgress() {
			
			@Override
			public void start(String file, long bytesTotal) {
				LOG.info("Starting transfer of {} ({} bytes)", file, bytesTotal);
				progress.maxHint(bytesTotal);
			}

			@Override
			public void progress(long bytesSoFar) {
				if(LOG.isDebugEnabled()) {
					LOG.info("Progress: {} bytes transferred", bytesSoFar);
				}
				progress.stepTo(bytesSoFar);
			}

			@Override
			public void done(Exception error) {
				LOG.info("Transfer done. {}.", error == null ? "No errors" : "Error: " + error.getMessage());
			}

			@Override
			public void message(String message) {
				progress.setExtraMessage(message);
			}
		};
	}

	protected Path defaultDownloadsDirectory() {
		return Strings.parseFilePath(parent.tty().ttyContext().getContainer().getConfiguration().transfers().get(Constants.DOWNLOADS_KEY));
	}
}
