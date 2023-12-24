package com.sshtools.pretty.pricli;

import org.jline.style.StyleExpression;
import org.jline.utils.AttributedStringBuilder;

public class Styling {

	public static AttributedStringBuilder styled(String text) {
		var astr = new AttributedStringBuilder();
		var se = new StyleExpression();
		se.evaluate(astr, text);
		return astr;
	}
}
