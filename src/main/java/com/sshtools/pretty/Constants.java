package com.sshtools.pretty;

public interface Constants {
	//
	// Sections
	//

	String UI_SECTION = "ui";
	String TERMINAL_SECTION = "terminal";
	String DEBUG_SECTION = "debug";
	String SERIAL_PORT_SECTION = "serial-port";
	String TRANSFERS_SECTION = "file-transfer";
	String PRICLI_SECTION = "pricli";
	String STATUS_SECTION = "status";
	String ENVIRONMENT_SECTION = "environment";

	//
	// Keys
	//

	// Terminal
	String THEME_KEY = "theme";
	String SHELL_KEY = "shell";
	String LOGIN_SHELL_KEY = "login-shell";
	String LEGACY_PTY_KEY = "legacy-pty";
	String WINDOWS_ANSI_COLOR_KEY = "windows-ansi-color";
	String PRESERVE_PTY_KEY = "preserve-tty";
	String CONSOLE_KEY = "console";
	String OPACITY_KEY = "opacity";
	String FONTS_KEY = "fonts";
	String BUFFER_SIZE_KEY = "buffer-size";
	String BUFFER_MODE_KEY = "buffer-mode";
	String LIMIT_BUFFER_KEY = "limit-buffer";
	String FONT_SIZE_KEY = "font-size";
	String RESIZE_STRATEGY_KEY = "resize-strategy";
	String SCREEN_SIZE_KEY = "screen-size";
	String ENABLED_KEY = "enabled";
	String SCROLL_BAR_KEY = "scroll-bar";
	String DISABLED_FEATURES = "disabled-features";
	String ENABLED_FEATURES = "enabled-features";
	String TYPE_KEY = "type";
	String BLINKING_KEY = "blinking";
	String CURSOR_BLINK_KEY = "cursor-blink";
	String CURSOR_STYLE_KEY = "cursor-style";
	String BACKGROUND_TYPE_KEY = "background-type";
	String BACKGROUND = "background";
	String COPY_ON_SELECT = "copy-on-select";
	String REFLOW_KEY = "reflow";

	// UI
	String DARK_MODE_KEY = "dark-mode";
	String ACCENT_COLOR_KEY = "accent-color";
	String MUTE_KEY = "mute";
	String PASSWORD_MODE_KEY = "password-mode";

	// Transfer
	String NOTIFICATIONS_KEY = "notifications";
	String DOWNLOADS_KEY = "downloads";
	String MOUNT_PATH = "mount-path";

	// Pricli
	String HEIGHT_KEY = "height";
	
	// Serial
	String DEVICE_KEY = "device";
	String BAUD_RATE_KEY = "baud-rate";
	String DATA_BITS_KEY = "data-bits";
	String STOP_BITS_KEY = "stop-bits";
	String PARITY_KEY = "parity";
	String FLOW_IN_KEY = "flow-out";
	String FLOW_OUT_KEY = "flow-in";
	String IN_BUFFER_SIZE_KEY = "in-buffer-size";
	String OUT_BUFFER_SIZE_KEY = "out-buffer-size";
	String RTS_KEY = "rts";
	String DTR_KEY = "dtr";
	
	// DEBUG
	String VERBOSE_EXCEPTIONS_KEY = "verbose-exceptions";
	String ALIGN_KEY = "align";

	//
	// Built-in variables
	//
	String ACTION_ON_VAR = "ACTION_ON";

}
