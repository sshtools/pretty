package com.sshtools.pretty;

import static javafx.application.Platform.runLater;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.pretty.Status.Element;
import com.sshtools.pretty.Status.Unit;
import com.sshtools.pretty.Status.Width;
import com.sshtools.terminal.emulation.TerminalViewport;
import com.sshtools.terminal.emulation.events.TransferListener;
import com.sshtools.terminal.emulation.transfers.Transfer;
import com.sshtools.terminal.emulation.transfers.Transfer.Direction;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;
import com.sshtools.twoslices.ToastBuilder;
import com.sshtools.twoslices.ToastType;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;

public class TransferHandler implements TransferListener {
	
	private final static class TransfersIndicator implements Element {
		
		private AtomicLong total = new AtomicLong();

		private final Map<Transfer, ActiveTransfer> accepted;		
		
		private final static String SHADED_DARK = Strings.repeat(256, '▓');
		private final static String SHADED_MEDIUM = Strings.repeat(256, '▒');
		private final static String SHADED_LIGHT = Strings.repeat(256, '░');
		
		private TransfersIndicator(Map<Transfer, ActiveTransfer> accepted) {
			this.accepted = accepted;
		}

		@Override
		public Width width() {
			return new Width(Unit.REMAINING, 15);
		}

		@Override
		public void draw(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp, int cols) throws IOException {
			var bldr = new AttributedStringBuilder();
			bldr.style(AttributedStyle.INVERSE);
			var completed = accepted.values().stream().collect(Collectors.summarizingLong(t -> t.progress.get())).getSum();
			var frac = Math.min(1.0, (float)((double)completed / total.doubleValue()));
			var pc = (int)((frac )* 100f);
			String background;
			var space = cols - 2;
			var bar = (int)((float)space * frac); 
			if(pc < 50) {
				background = Strings.trimPad(SHADED_LIGHT, bar);
			}
			else if(pc < 75) {
				background = Strings.trimPad(SHADED_MEDIUM, bar);
			}
			else {
				background = Strings.trimPad(SHADED_DARK, bar);
			}
			background += Strings.trimPad(" ", space - bar);
			if(cols > 8) {
				var bl = background.length();
				var pcs = pc + "%";
				var pcl = pcs.length();
				var bs = (bl / 2) - (pcl / 2);
				background = background.substring(0, bs) + pcs + background.substring(bs + pcl);
			}
			bldr.append("[" + background + "]");
			bldr.style(AttributedStyle.INVERSE_OFF);
			vp.write(bldr.toAnsi().getBytes(vp.getCharacterSet()));
			
		}		
	}
	
	static Logger LOG = LoggerFactory.getLogger(TransferHandler.class);
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(TransferHandler.class.getName());
	
	private class  ActiveTransfer {
		final Path targetfile;
		final WritableByteChannel chan;
		final AtomicLong progress = new AtomicLong();
		public ActiveTransfer(Path targetfile, WritableByteChannel chan) {
			super();
			this.targetfile = targetfile;
			this.chan = chan;
		}
		
		
	}

	private TTY tty;
	private Map<Transfer, ActiveTransfer> accepted = Collections.synchronizedMap(new HashMap<>());
	private final TransfersIndicator transferStatus;
	private AnimationTimer animTimer;
	private final AtomicLong updates = new AtomicLong();

	public TransferHandler(TTY tty) {
		this.tty = tty;
		transferStatus = new TransfersIndicator(accepted);
		animTimer = new AnimationTimer() {
			@Override
			public void handle(long now) {
				if (updates.getAndSet(0) > 0) {
					tty.status().redraw(false);
				}
			}
		};
	}

	@Override
	public Optional<WritableByteChannel> started(Transfer transfer) throws IOException {
		if(transfer.direction() == Direction.DOWNLOAD) {

			
			var sem = new Semaphore(1);
			var chanResult = new AtomicReference<WritableByteChannel>();
			try {
				sem.acquire();
			    
				Platform.runLater(() -> {

						/*TODO configurable directory, and maybe option at download time */
					    var dir = Strings.parseFilePath(
					    		tty.ttyContext().getContainer().getConfiguration().transfers().get(Constants.DOWNLOADS_KEY));;
					    var file = dir.resolve(transfer.filename().orElse(RESOURCES.getString("unknownFile")));
	
					    transfer.size().ifPresent(sz -> transferStatus.total.addAndGet(sz));
						checkIndicator();
					
						var alert = new Alert(AlertType.CONFIRMATION);
						alert.initOwner(tty.ttyContext().stage());
						alert.setTitle(RESOURCES.getString("downloadTitle"));
						alert.setHeaderText(RESOURCES.getString("download"));
						alert.setContentText(MessageFormat.format(RESOURCES.getString("downloadText"), file.getFileName()));
						
						var accept = new ButtonType(RESOURCES.getString("accept"));
						var cancel = new ButtonType(RESOURCES.getString("reject"), ButtonData.CANCEL_CLOSE);
		
						alert.getButtonTypes().setAll(accept, cancel);
		
						var result = alert.showAndWait();
						synchronized(accepted) {
							if (result.isPresent() && result.get() == accept) {
								
								try {
								    if(!Files.exists(dir)) {
										Files.createDirectories(dir);
								    }
								    var chan = Files.newByteChannel(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
								    accepted.put(transfer, new ActiveTransfer(file, chan));
								    chanResult.set(chan);
								}
								catch(IOException ioe) {
									/* TODO better error handling here, and option to retry */
									LOG.error("Failed to create file for download.", ioe);
								}
								checkIndicator();
							} 	
							else {
								LOG.info("Rejecting file.");
								checkIndicator();
							}
						}
						
						sem.release();
				});
				
				try {
					sem.acquire();
					return Optional.ofNullable(chanResult.get());
				}
				finally {
					sem.release();
				}
			}
			catch(InterruptedException ie) {
				throw new IOException("Interrupted while waiting for user to accept download.", ie);	
			}
		}
		else 
			throw new IllegalStateException();
	}

	@Override
	public void progress(Transfer transfer, long amount) {
		transfer.size().ifPresent(sz -> { 
			var active = accepted.get(transfer);
			 active.progress.getAndAdd(amount);
			updates.incrementAndGet();
		});
	}

	@Override
	public void complete(Transfer transfer, Optional<Exception> error) {
		synchronized(accepted) {
			var active = accepted.get(transfer);
			if(active == null) {
				LOG.debug("Transfer was already rejected.");
			}
			else {
			    transfer.size().ifPresent(sz ->  { 
			    	transferStatus.total.addAndGet(-sz);  
			    	runLater(() -> tty.status().redraw(false));
			    });
				try {
					active.chan.close();
				}
				catch(IOException ioe) {
					throw new UncheckedIOException(ioe);
				}
				finally {

					if(tty.ttyContext().getContainer().getConfiguration().transfers().getBoolean(Constants.NOTIFICATIONS_KEY)) {
						new ToastBuilder().
							type(ToastType.INFO).
							action(RESOURCES.getString("openFolder"), () -> {
								tty.ttyContext().getContainer().getHostServices().showDocument(active.targetfile.getParent().toUri().toString());
							}).
							title(RESOURCES.getString("downloadComplete")).
							content(MessageFormat.format(RESOURCES.getString("downloadCompleteText"), transfer.filename().orElse(RESOURCES.getString("unknownFile")))).
							toast();
					}
				}
			}
		}
	}

	
	private void checkIndicator() {
		var shouldShow = !accepted.isEmpty();
		var isShowing = tty.status().has(transferStatus);
		if(shouldShow && !isShowing) {
			runLater(() -> { 
				tty.status().add(transferStatus);
				animTimer.start();
			});
		}
		else if(!shouldShow && isShowing) {
			runLater(() -> { 
				tty.status().remove(transferStatus); 
			});			
		}
	}

}
