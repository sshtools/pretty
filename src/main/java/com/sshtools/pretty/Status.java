package com.sshtools.pretty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.sshtools.terminal.emulation.Modes;
import com.sshtools.terminal.emulation.SGRState;
import com.sshtools.terminal.emulation.TerminalViewport;
import com.sshtools.terminal.emulation.Viewport;
import com.sshtools.terminal.emulation.events.ViewportEvent;
import com.sshtools.terminal.emulation.events.ViewportListener;
import com.sshtools.terminal.vt.javafx.JavaFXTerminalPanel;

public class Status {
	
	private final static int MIN_ELEMENT_WIDTH = 3;
	
	public enum Unit {
		PERCENT, COLUMNS, REMAINING, FIXED
	}
	
	public record Width(Unit unit, int amount) {}
	
	public record Bounds(int x, int width) {}

	public interface Element {
		Width width();

		default void added(Status status) {
			
		}
		
		default void attached(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp) {
		}
		
		void draw(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp, int cols) throws IOException;

		default void removed(Status status) {
		}
		
		default void detached(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp) {
		}
	}
	
	public final static class InsertReplaceMode implements Element, ViewportListener {
		
		private final TerminalViewport<JavaFXTerminalPanel, ?, ?> terminal;
		private Status status;

		public InsertReplaceMode(TTY tty) {
			terminal = tty.terminal().getViewport();
			terminal.addTerminalBufferListener(this);
		}

		@Override
		public void added(Status status) {
			this.status = status;
		}

		@Override
		public void modesChanged(Modes mode) {
			status.redraw();
		}

		@Override
		public Width width() {
			return new Width(Unit.FIXED, 3);
		}

		@Override
		public void draw(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp, int cols) throws IOException {
			var bldr = new AttributedStringBuilder();
			bldr.style(AttributedStyle.INVERSE);
			bldr.append(Strings.trimPad(terminal.getModes().isInsertMode() ? "Ins" : "Rep", cols));
			bldr.style(AttributedStyle.INVERSE_OFF);
			vp.write(bldr.toAnsi().getBytes(vp.getCharacterSet()));
			
		}		
	}
	
	public final static class SizeAndCursor implements Element, ViewportListener {
		
		private final Viewport<?, ?> terminal;
		private Status status;

		public SizeAndCursor(TTY tty) {
			terminal = tty.terminal().getViewport();
			terminal.addTerminalBufferListener(this);
		}

		@Override
		public void added(Status status) {
			this.status = status;
		}

		@Override
		public void screenResize(int cols, int rows, boolean remote, ViewportEvent event) {
			status.redraw();
		}

		@Override
		public void cursorChanged(int cursorX, int cursorY) {
			status.redraw();
		}

		@Override
		public void modesChanged(Modes mode) {
			status.redraw();
		}

		@Override
		public Width width() {
			return new Width(Unit.FIXED, 15);
		}

		@Override
		public void draw(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp, int cols) throws IOException {
			var bldr = new AttributedStringBuilder();
			bldr.style(AttributedStyle.INVERSE);
			bldr.append(Strings.trimPad( 
				String.format("%dx%d %d,%d", terminal.getColumns(), terminal.getRows(), terminal.getPage().cursorX(), terminal.getPage().cursorY())
			, cols));
			bldr.style(AttributedStyle.INVERSE_OFF);
			vp.write(bldr.toAnsi().getBytes(vp.getCharacterSet()));
			
		}		
	}
	
	private List<Element> elements = new CopyOnWriteArrayList<>();
	private TerminalViewport<JavaFXTerminalPanel, ?, ?> vp;
	private Map<Element, Bounds> layout = Collections.emptyMap();
	private int lastLayoutCols;
	
	public void redraw() {
		draw(null);
	}

	public boolean has(Element element) {
		return elements.contains(element);
	}
	
	public void draw(Element element) {
		if(vp !=  null) {
			layout();
			if(element == null) {
				vp.deleteLine(0, SGRState.none());
				elements.forEach(this::doDraw);
			}
			else {
				doDraw(element);
			}
		}
	}

	private void doDraw(Element element) {
		try {
			var bnds = layout.get(element);
			vp.setCursorPosition(bnds.x(), 0);
			element.draw(vp, bnds.width());
		}
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}
	
	public void detach() {
		if(vp != null) {
			try {
				elements.forEach(e -> e.detached(vp));
			}
			finally {
				vp = null;
			}
		}
	}
	
	public void attach(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp) {
		this.vp = vp;
		lastLayoutCols = -1;
		try {
			elements.forEach(e -> e.detached(vp));
		}
		finally {
			redraw();
		}
	}
	
	public void add(Element element) {
		lastLayoutCols = -1;
		element.added(this);
		elements.add(element);
		layout();
		redraw();
	}
	
	public void remove(Element element) {
		lastLayoutCols = -1;
		elements.remove(element);
		element.removed(this);
		layout();
		redraw();
	}

	public boolean isAttached() {
		return vp != null;
	}

	private void layout() {
		if(vp != null ) {
			/* Available space for whole indicator line. This is the width of the viewport,
			 * less <code>columns - 1</code> for the inter-element gap */
			var available = vp.getColumns() - (Math.max(0, elements.size() - 1));
			var layoutElements = elements;
			
			if(available != lastLayoutCols) {
			
				/* For each element, total up how much space it actually wants */
				var wanted = 0;
				var map = new HashMap<Element, Integer>();
				for(var el : layoutElements) {
					var w = el.width();
					var c = w.amount;
					if(w.unit == Unit.PERCENT) {
						c = (int)(((float)available / 100f) * (float)w.amount);
					}
					c = Math.max(MIN_ELEMENT_WIDTH, c);
					map.put(el, c);
					wanted += c;
				}
				
				var resizableElements = layoutElements.stream().filter(el -> el.width().unit != Unit.FIXED).toList();
				
				/* If the total exceeds the available space, then shrink each element 
				 * proportionally so that all elements will 
				 */
				if(wanted > available) {
					var shrinkEach = Math.max(1, (int)((float)(wanted - available ) / (float)layoutElements.size()));
					var newWidth = 0;
					for(int i = 0 ; i < resizableElements.size(); i++) {
						var el = resizableElements.get(i);
						if(i == resizableElements.size() - 1) {
							/* Last element might get an extra cell */
							map.put(el, available - newWidth);
						}
						else {
							var nw = map.get(el) - shrinkEach;
							map.put(el, nw);
							newWidth += nw;
						}
					}
				}
				else {
					var excess = available - wanted;
					
					/* There is excess space. If there are any elements that declare they will
					 * take excess space, divide it among them.
					 * 
					 * If there are no such elements, divide the remaining space amongst all 
					 * elements
					 */
					var toGrow = resizableElements.stream().filter(e -> e.width().unit == Unit.REMAINING).toList();
					if(toGrow.isEmpty()) {
						toGrow = resizableElements;					
					}
					var growEach = Math.max(1, (int)((float)excess / (float)toGrow.size()));
					var remain = excess;
					for(int i = 0 ; i < toGrow.size(); i++) {
						var el = toGrow.get(i);
						if(i == toGrow.size() - 1) {
							/* Last element might get an extra cell */
							map.put(el, map.get(el) + remain);
						}
						else {
							var nw = map.get(el) + growEach;
							map.put(el, nw);
							remain -= growEach;
						}
					}
				}
				
				var layout = new LinkedHashMap<Element, Bounds>();
				var x = 0;
				for(var el : layoutElements) {
					var w = map.get(el);
					layout.put(el, new  Bounds(x, w));
					x += w.intValue() + 1;
				}
				this.layout = Collections.unmodifiableMap(layout);
			}
		}
		else {
			this.layout = Collections.emptyMap();
		}
	}
}
