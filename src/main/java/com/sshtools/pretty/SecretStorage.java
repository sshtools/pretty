package com.sshtools.pretty;

import java.util.function.Supplier;

public interface SecretStorage {
	
	public static record Key(String protocol, String username, String serviceName) {}

	void reset(Key key);

	char[] secret(Key key, Supplier<char[]> prompt);
}