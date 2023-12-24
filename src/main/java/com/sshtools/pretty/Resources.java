package com.sshtools.pretty;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class Resources {

	public final static String messageOrDefault(ResourceBundle bundle, String key) {
		return messageOrDefault(bundle, key, key);
	}

	public final static String messageOrDefault(ResourceBundle bundle, String key, String defaultValue) {
		try {
			return bundle.getString(key);
		} catch (MissingResourceException mre) {
			return defaultValue;
		}
	}
}
