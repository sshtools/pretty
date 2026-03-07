package com.sshtools.pretty;

import java.io.Closeable;
import java.util.Optional;

public interface TerminalProtocol extends Closeable {
	
	void attach(TTY tty) throws Exception;

	void detach();
	
	@Override
	void close();

	String displayName();
	
	Optional<String> canClose();

	boolean isAttached();

	void cwd(String cwd);

}