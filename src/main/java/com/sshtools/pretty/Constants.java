package com.sshtools.pretty;

public class Constants {
	//
	// Sections
	//

	public final static String UI_SECTION = "ui";
	public static final String TERMINAL_SECTION = "terminal";
	public static final String DEBUG_SECTION = "debug";
	public static final String TRANSFERS_SECTION = "file-transfer";
	public static final String PRICLI_SECTION = "pricli";
	public static final String STATUS_SECTION = "status";
	public static final String ENVIRONMENT_SECTION = "environment";
	
	//
	// Keys
	//
	
	// Terminal
	public static final String THEME_KEY = "theme";
	public static final String SHELL_KEY = "shell";
	public static final String LOGIN_SHELL_KEY = "login-shell";
	public static final String LEGACY_PTY_KEY = "legacy-pty";
	public static final String WINDOWS_ANSI_COLOR_KEY = "windows-ansi-color";
	public static final String PRESERVE_PTY_KEY = "preserve-tty";
	public static final String CONSOLE_KEY = "console";
	public static final String OPACITY_KEY = "opacity";
	public static final String FONTS_KEY = "fonts";
	public static final String BUFFER_SIZE_KEY = "buffer-size";
	public static final String BUFFER_MODE_KEY = "buffer-mode";
	public static final String LIMIT_BUFFER_KEY = "limit-buffer";
	public static final String FONT_SIZE_KEY = "font-size";
	public static final String RESIZE_STRATEGY_KEY = "resize-strategy";
	public static final String SCREEN_SIZE_KEY = "screen-size";
	public static final String ENABLED_KEY = "enabled";
	public static final String SCROLL_BAR_KEY = "scroll-bar";
	public static final String DISABLED_FEATURES = "disabled-features";
	public static final String ENABLED_FEATURES = "enabled-features";
	public static final String TYPE_KEY = "type";
	public static final String BLINKING_KEY = "blinking";
	public static final String CURSOR_BLINK_KEY = "cursor-blink";
	public static final String CURSOR_STYLE_KEY = "cursor-style";
	public static final String BACKGROUND_TYPE_KEY = "background-type";
	public static final String BACKGROUND = "background";
	
	// UI
	public static final String DARK_MODE_KEY = "dark-mode";
	public static final String ACCENT_COLOR_KEY = "accent-color";
	public static final String MUTE_KEY = "mute";
	public static final String PASSWORD_MODE_KEY = "password-mode";
	
	// Transfer
	public static final String NOTIFICATIONS_KEY = "notifications";
	public static final String DOWNLOADS_KEY = "downloads";
	
	// Pricli
	public static final String HEIGHT_KEY = "height";
	
	// DEBUG
	public static final String VERBOSE_EXCEPTIONS_KEY = "verbose-exceptions";
	public static final String ALIGN_KEY = "align";
}
