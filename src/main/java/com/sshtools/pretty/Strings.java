package com.sshtools.pretty;

import java.util.ArrayList;
import java.util.List;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

public class Strings {
	
	public static String trimPad(String str, int len) {
		if(str == null)
			return null;
		else if(len < 0) {
			return "";
		}
		else if(str.length() > len) {
			return str.substring(0, len);
		}
		else if(str.length() < len) {
			return String.format("%-" + len + "s", str);
		}
		else {
			return str;
		}
	}

	public static int parseCharSpec(String charSpec) {
		if(charSpec == null || charSpec.trim().equals("")) {
			return 0;
		}
		else if(charSpec.matches("u[0-9a-fA-F]+")) {
			return Integer.parseInt(charSpec.substring(1));
		}
		else if(charSpec.matches("\\#")) {
			return (int)charSpec.charAt(1);
		}
		else if(charSpec.matches("[0-9]+")) {
			return Integer.parseInt(charSpec);
		}
		else if(charSpec.length() == 1) {
			return (int)charSpec.charAt(1);
		} else {
			throw new IllegalArgumentException("Illeged character spec.");
		}
	}
	
	public static List<String> parseQuotedString(String command) {
		var args = new ArrayList<String>();
		var escaped = false;
		var quoted = false;
		var word = new StringBuilder();
		for (int i = 0; i < command.length(); i++) {
			char c = command.charAt(i);
			if (c == '"' && !escaped) {
				if (quoted) {
					quoted = false;
				} else {
					quoted = true;
				}
			} else if (c == '"' && !escaped) {
				escaped = true;
			} else if (c == ' ' && !escaped && !quoted) {
				if (word.length() > 0) {
					args.add(word.toString());
					word.setLength(0);
					;
				}
			} else {
				word.append(c);
			}
		}
		if(escaped)
			throw new IllegalArgumentException("Invalid escape.");
		if(quoted)
			throw new IllegalArgumentException("Unbalanced quotes.");
		if (word.length() > 0)
			args.add(word.toString());
		return args;
	}

	public static boolean parseBooleanSpec(String s) {
		if(s == null)
			return false;
		s = s.toLowerCase();
		if(s.equals("true") || s.equals("on") || s.equals("1") || s.equals("+")) {
			return true;
		}
		else if(s.equals("false") || s.equals("off") || s.equals("0") || s.equals("-")) {
			return false;
		}
		else {
			throw new IllegalArgumentException("Illegal boolean spec");
		}
	}

	public static String repeat(int len, char c) {
		var b = new StringBuilder();
		for(int i = 0 ; i < len ; i++) {
			b.append(c);
		}
		return b.toString();
	}

	public static AttributedStringBuilder ansiExceptionString(boolean verbose, Exception ex, String msg) {
		var report = new AttributedStringBuilder();
		report.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		report.append(msg);
		report.style(AttributedStyle.DEFAULT.foregroundDefault());
		if(verbose) {
			Throwable nex = ex;
			int indent = 0;
			while(nex != null) {
				report.append(System.lineSeparator());
				if(indent > 0) {
					report.append(String.format("%" + ( 8 + ((indent - 1 )* 2) ) + "s", ""));
					report.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
					report.append(nex.getMessage() == null ? "No message." : nex.getMessage());
					report.style(AttributedStyle.DEFAULT.foregroundDefault());
					report.append(System.lineSeparator());
				}
				
				for(var el : nex.getStackTrace()) {
					report.append(System.lineSeparator());
					report.append(String.format("%" + ( 8 + (indent * 2) ) + "s", ""));
					report.append("at ");
					if(el.getModuleName() != null) {
						report.append(el.getModuleName());
						report.append('/');
					}
					report.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
					report.append(el.getClassName());
					report.append('.');
					report.append(el.getMethodName());
					report.style(AttributedStyle.DEFAULT.foregroundDefault());
					if(el.getFileName() != null) {
						report.append('(');
						report.append(el.getFileName());
						if(el.getLineNumber() > -1) {
							report.append(':');
							report.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
							report.append(String.valueOf(el.getLineNumber()));
							report.style(AttributedStyle.DEFAULT.foregroundDefault());
							report.append(')');
						}
					}
				}
				indent++;
				nex = nex.getCause();
			}
		}
		return report;
	}
}
