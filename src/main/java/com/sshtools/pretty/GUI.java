package com.sshtools.pretty;

import java.util.concurrent.Callable;

public class GUI {

	public static <T> T runAndBlock(Callable<T> callable) {
		if (javafx.application.Platform.isFxApplicationThread()) {
			try {
				return callable.call();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else {
			final var result = new java.util.concurrent.atomic.AtomicReference<T>();
			final var exception = new java.util.concurrent.atomic.AtomicReference<Exception>();
			javafx.application.Platform.runLater(() -> {
				try {
					result.set(callable.call());
				} catch (Exception e) {
					exception.set(e);
				}
			});
			while (result.get() == null && exception.get() == null) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new RuntimeException(e);
				}
			}
			if (exception.get() != null) {
				throw new RuntimeException(exception.get());
			}
			return result.get();
		}
	}
}
