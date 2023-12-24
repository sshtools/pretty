package com.sshtools.pretty;

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
}
