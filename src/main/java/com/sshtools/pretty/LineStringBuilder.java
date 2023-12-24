package com.sshtools.pretty;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;

public class LineStringBuilder extends AttributedStringBuilder {
	
	private boolean doubleLines;
	private LineStyle lineStyle;

	public LineStringBuilder style(LineStyle style) {
		doubleLines |= style.isDoubleHeight();
		lineStyle = style;
		return this;
	}

	@Override
	public AttributedString toAttributedString() {
		if(doubleLines) {
			return AttributedString.join(
				new AttributedString("\n"), 
				new AttributedString(lineStyle.toAnsi(true) + toAnsi()),
				new AttributedString(lineStyle.toAnsi(false) + toAnsi())
			);
		}
		else {
			return new AttributedString(lineStyle.toAnsi(true) + toAnsi());
		}
	}

}
