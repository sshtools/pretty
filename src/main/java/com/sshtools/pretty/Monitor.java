package com.sshtools.pretty;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Monitor {

	@FunctionalInterface
	public interface OnChange {
		void onChange(WatchEvent<Path> evt);
	}

	@FunctionalInterface
	public interface MonitorHandle extends Closeable {
		@Override
		void close();
	}

	private record Wrapped(OnChange callback, Path dir) {
	}

	private record Waiting(ScheduledFuture<?> task, Kind<?> kind) {
	}

	private final WatchService watchService;
	private final Map<WatchKey, Wrapped> callbacks = Collections.synchronizedMap(new HashMap<>());
	private final Map<String, Waiting> pending = Collections.synchronizedMap(new HashMap<>());

	@SuppressWarnings("unchecked")
	public Monitor(AppContext ctx) {
		try {
			watchService = FileSystems.getDefault().newWatchService();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		var thr = new Thread(() -> {
			while (true) {
				// wait for key to be signaled
				WatchKey key;
				try {
					key = watchService.take();
				} catch (InterruptedException x) {
					return;
				}

				synchronized (pending) {

					var onChange = callbacks.get(key);

					for (var event : key.pollEvents()) {
						var kind = event.kind();
						if (kind == StandardWatchEventKinds.OVERFLOW) {
							continue;
						}

						var path = (Path) event.context();
						var full = onChange.dir.resolve(path);
						var fullStr = full.toString();
						var pnd = pending.get(fullStr);

						if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
							if (pnd != null) {
								pnd.task.cancel(false);
							}
							pending.put(fullStr, new Waiting(ctx.scheduler().schedule(() -> {
								synchronized (pending) {
									onChange.callback.onChange((WatchEvent<Path>) event);
									pending.remove(fullStr);
								}
							}, 1, TimeUnit.MILLISECONDS), kind));
						} else if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
							if (pnd != null) {
								pnd.task.cancel(false);
							}
							pending.put(fullStr, new Waiting(ctx.scheduler().schedule(() -> {
								synchronized (pending) {
									onChange.callback.onChange((WatchEvent<Path>) event);
									pending.remove(fullStr);
								}
							}, 1, TimeUnit.SECONDS), kind));
						} else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
							if ((pnd != null && pnd.kind == StandardWatchEventKinds.ENTRY_MODIFY) || pnd == null) {
								if (pnd != null) {
									pnd.task.cancel(false);
								}
								pending.put(fullStr, new Waiting(ctx.scheduler().schedule(() -> {
									synchronized (pending) {
										onChange.callback.onChange((WatchEvent<Path>) event);
										pending.remove(fullStr);
									}
								}, 1, TimeUnit.SECONDS), kind));
							} /*else if (pnd != null && pnd.kind != StandardWatchEventKinds.ENTRY_MODIFY) {
								System.out.println("MOD2");
								pnd.task.cancel(false);
							}*/
						}
					}

				}

				key.reset();
			}
		}, "FileMonitor");
		thr.setDaemon(true);
		thr.start();
	}

	public MonitorHandle monitor(Path path, OnChange cb) {
		try {
			var key = path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
					StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
			callbacks.put(key, new Wrapped(cb, path));
			return new MonitorHandle() {
				@Override
				public void close() {
					key.cancel();
					callbacks.remove(key);
				}
			};
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}
}
