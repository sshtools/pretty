package com.sshtools.pretty;

import java.io.Closeable;
import java.util.Optional;

public interface TerminalProtocol extends Closeable {
	
	void attach(TTY tty);

	void detach();
	
	@Override
	void close();
	
	void decode() throws Exception;

	String displayName();
	
	Optional<String> canClose();

	boolean isAttached();

	void cwd(String cwd);
	
	default void send(byte[] data, int off, int len) {
		throw new UnsupportedOperationException("This protocol does not support sending data.");
	}

}