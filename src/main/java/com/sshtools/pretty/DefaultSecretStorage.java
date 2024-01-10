package com.sshtools.pretty;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javakeyring.BackendNotSupportedException;
import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;

public class DefaultSecretStorage implements SecretStorage {

	static Logger LOG = LoggerFactory.getLogger(DefaultSecretStorage.class);

	private PasswordMode mode;
	private Map<Key, char[]> passwords = new HashMap<>();
	private Optional<Keyring> keyring;

	public DefaultSecretStorage(AppContext ctx) {
		ctx.getConfiguration().bindEnum(PasswordMode.class, m -> mode = m, () -> mode, Constants.PASSWORD_MODE_KEY,
				Constants.UI_SECTION);
	}

	@Override
	public void reset(Key key) {
		passwords.remove(key);
		if (keyring != null || mode == PasswordMode.STORE) {
			checkKeyring().ifPresent(kw -> {
				try {
					kw.deletePassword(key.protocol() + ":" + key.serviceName(), key.username());
				} catch (PasswordAccessException e) {
					LOG.warn("Failed to delete entry from keyring.", e);
				}
			});
		}
	}

	@Override
	public char[] secret(Key key, Supplier<char[]> prompt) {
		char[] pw;

		var serviceKey = key.protocol() + ":" +  key.serviceName();
		if (mode == PasswordMode.STORE && checkKeyring().isPresent()) {
			try {
				return keyring.get().getPassword(serviceKey, key.username()).toCharArray();
			} catch (PasswordAccessException e) {
				LOG.error("Key ring error", e);
			}
		}

		if (mode == PasswordMode.SESSION) {
			pw = passwords.get(key);
			if (pw != null) {
				return pw;
			}
		}

		pw = prompt.get();
		if (pw != null) {
			if (mode == PasswordMode.STORE) {
				try {
					LOG.info("Storing password for {}@{} in keyring.", key.username(), serviceKey);
					keyring.get().setPassword(serviceKey, key.username(), new String(pw));
				} catch (PasswordAccessException e) {
					LOG.warn("Failed to store entry in keyring.", e);
				}
			} else if (mode == PasswordMode.SESSION) {
				LOG.info("Storing password for {}@{} in session.", key.username(), serviceKey);
				passwords.put(key, pw);
			}
		}

		return pw;
	}

	private Optional<Keyring> checkKeyring() {
		if (keyring == null) {
			setupKeychain();
		}
		return keyring;
	}

	private void setupKeychain() {
		try {
			keyring = Optional.of(Keyring.create());
			LOG.info("Loaded key ring");
		} catch (BackendNotSupportedException e) {
			LOG.error("No support for key ring", e);
			keyring = Optional.empty();
		}
	}
}
