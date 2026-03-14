package com.sshtools.pretty;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.sshtools.terminal.emulation.TerminalInput;
import com.sshtools.terminal.emulation.TerminalViewport;

public class TerminalInputMonitor implements Closeable, TerminalInput {

	public final static class Builder {
		
		private final TerminalViewport<?, ?, ?> viewport;
		private final Map<Byte, Consumer<Byte>> handlers = new HashMap<>();
		private Thread mainThread = Thread.currentThread();

		public Builder(TerminalViewport<?, ?, ?> viewport) {
			this.viewport = viewport;
		}
		
		public Builder withThread(Thread thread) {
			this.mainThread = thread;
			return this;
		}
		
		public Builder onTerminateInterrupt() {
			return on((byte) 0x03, c -> mainThread.interrupt());
		}
		
		public Builder on(byte input, Consumer<Byte> consumer) {
			handlers.put(input, consumer);
			return this;
		}
		
		public TerminalInputMonitor build() {
			return new TerminalInputMonitor(this);
		}
	}

	private final TerminalViewport<?, ?, ?> viewport;
	private final TerminalInput was;
	private final Map<Byte, Consumer<Byte>> handlers;
	
	private TerminalInputMonitor(Builder builder) {
		this.viewport = builder.viewport;
		this.handlers = Collections.unmodifiableMap(new HashMap<>(builder.handlers));
		
		was = viewport.setInput(this);
	}

	@Override
	public void close(){
		viewport.setInput(was);
	}

	@Override
	public void input(byte[] data, int offset, int len) throws IOException {
		for (int i = 0; i < len; i++) {
			byte b = data[offset + i];
			var handler = handlers.get(b);
			if (handler != null) {
				handler.accept(b);
			}
		}
	}

}
