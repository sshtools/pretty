package com.sshtools.pretty;

import org.jline.utils.AttributedStyle;

public class LineStyle extends AttributedStyle {
	

    public static final LineStyle DEFAULT = new LineStyle();
    public static final LineStyle DOUBLE_WIDTH = DEFAULT.doubleWidth();
    public static final LineStyle DOUBLE_HEIGHT = DEFAULT.doubleHeight();
    public static final LineStyle DOUBLE_WIDTH_AND_HEIGHT = DEFAULT.doubleWidth().doubleHeight();
    
    static final long F_DOUBLE_WIDTH = 0x00010000;
    static final long F_DOUBLE_HEIGHT = 0x00020000;
    
    public LineStyle() {
		super();
	}

	public LineStyle(AttributedStyle s) {
		super(s);
	}

	public LineStyle(long style, long mask) {
		super(style, mask);
	}

	public LineStyle doubleWidth() {
        return new LineStyle(getStyle() | F_DOUBLE_WIDTH, getMask() | F_DOUBLE_WIDTH);
    }

    public AttributedStyle doubleWidthOff() {
        return new LineStyle(getStyle() & ~F_DOUBLE_WIDTH, getMask()  | F_DOUBLE_WIDTH);
    }

    public AttributedStyle doubleWidthDefault() {
        return new LineStyle(getStyle() & ~F_DOUBLE_WIDTH, getMask()  & ~F_DOUBLE_WIDTH);
    }

	public LineStyle doubleHeight() {
        return new LineStyle(getStyle() | F_DOUBLE_HEIGHT, getMask() | F_DOUBLE_HEIGHT);
    }

    public AttributedStyle doubleHeightOff() {
        return new LineStyle(getStyle() & ~F_DOUBLE_HEIGHT, getMask()  | F_DOUBLE_HEIGHT);
    }

    public AttributedStyle doubleHeightDefault() {
        return new LineStyle(getStyle() & ~F_DOUBLE_HEIGHT, getMask()  & ~F_DOUBLE_HEIGHT);
    }
    
    boolean isDoubleHeight() {
    	return ( getStyle() & F_DOUBLE_HEIGHT ) != 0l;
    }
    
    boolean isDoubleWidth() {
    	return ( getStyle() & F_DOUBLE_WIDTH ) != 0l;
    }

	public String toAnsi(boolean top) {
		var dh = isDoubleHeight();
		var dw = isDoubleWidth();
		if(dh && dw) {
			if(top)
				return (char)27 + "#3";
			else
				return (char)27 + "#4";
		}
		else if(dw) {
			return (char)27 + "#6";
		}
		else {
			return (char)27 + "#5";
		}
	}
}
