package com.sshtools.pretty;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.stage.Stage;

public class Bounds {

	public static Rectangle2D add(Rectangle2D bounds, Point2D offset) {
		return new Rectangle2D(bounds.getMinX() + offset.getX(), bounds.getMinY() + offset.getY(), bounds.getWidth(),
				bounds.getHeight());
	}

	public static Rectangle2D bounds(Stage stage) {
		return new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
	}

	public static Rectangle2D parseBounds(String str) {
		var a = str.split(",");
		if (a.length == 4)
			return new Rectangle2D(Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]),
					Double.parseDouble(a[3]));
		else
			return Rectangle2D.EMPTY;
	}

	public static String boundsString(Rectangle2D rect) {
		return String.format("%f,%f,%f,%f", rect.getMinX(), rect.getMinY(), rect.getWidth(), rect.getHeight());
	}
}
