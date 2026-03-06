package com.sshtools.pretty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.sshtools.terminal.emulation.Modes;
import com.sshtools.terminal.emulation.SGRState;
import com.sshtools.terminal.emulation.TerminalViewport;
import com.sshtools.terminal.emulation.Viewport;
import com.sshtools.terminal.emulation.buffer.BufferData;
import com.sshtools.terminal.emulation.buffer.BufferData.ConfigurationChange;
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
		}

		@Override
		public void attached(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp) {
			vp.addViewportListener(this);
		}

		@Override
		public void detached(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp) {
			vp.removeViewportListener(this);
		}

		@Override
		public void added(Status status) {
			this.status = status;
		}

		@Override
		public void modesChanged(Modes mode) {
			status.redraw(true);
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
		}

		@Override
		public void attached(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp) {
			terminal.addViewportListener(this);
		}

		@Override
		public void detached(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp) {
			terminal.removeViewportListener(this);
		}

		@Override
		public void added(Status status) {
			this.status = status;
		}

		@Override
		public void screenResize(int cols, int rows, boolean remote, ViewportEvent event) {
			status.redraw(true);
		}

		@Override
		public void cursorChanged(int cursorX, int cursorY) {
			status.redraw(false);
		}

		@Override
		public void modesChanged(Modes mode) {
			status.redraw(true);
		}

		@Override
		public Width width() {
			return new Width(Unit.FIXED, 15);
		}

		@Override
		public void bufferConfigurationChanged(ViewportEvent vp, BufferData data, ConfigurationChange... changes) {
			status.redraw(false);
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
	private ScheduledFuture<?> anim;
	private volatile boolean needsRedraw;
	private volatile boolean clearOnRedraw;
	
	public void redraw(boolean clear) {
		needsRedraw = true;
		clearOnRedraw |= clear;
	}

	public boolean has(Element element) {
		return elements.contains(element);
	}
	
	public void detach() {
		if(vp == null) {
			throw new IllegalStateException("Not attached to a viewport");
		}
		else {
			anim.cancel(false);
			try {
				elements.forEach(e -> e.detached(vp));
			}
			finally {
				vp = null;
				redraw(true);
			}
		}
	}
	
	public void attach(TerminalViewport<JavaFXTerminalPanel, ?, ?> vp) {
		if(this.vp != null) {
			throw new IllegalStateException("Already attached to a viewport");
		}
		
		this.vp = vp;
		
		/* We could get events (and so requests to redraw) very rapidly,
		 * so we schedule a redraw task at a fixed rate, only actually redrawing
		 * if the redraw() method was called in the meantime. 
		 */
		anim = vp.getScheduler().scheduleAtFixedRate(this::actuallyRedraw, 20, 20, TimeUnit.MILLISECONDS);
		
		lastLayoutCols = -1;
		try {
			elements.forEach(e -> e.attached(vp));
		}
		finally {
			redraw(true);
		}
	}
	
	public void add(Element element) {
		lastLayoutCols = -1;
		element.added(this);
		elements.add(element);
		layout();
		redraw(true);
	}
	
	public void remove(Element element) {
		lastLayoutCols = -1;
		elements.remove(element);
		element.removed(this);
		layout();
		redraw(true);
	}

	public boolean isAttached() {
		return vp != null;
	}
	
	private void actuallyRedraw() {
		if(needsRedraw) {
			try {
				draw(clearOnRedraw);
			}
			finally {
				clearOnRedraw = false;
				needsRedraw = false;
			}
		}
	}
	
	private void draw(boolean clear) {
		if(vp !=  null) {
			if(clear) {
				layout();
				vp.deleteLine(0, SGRState.none());
			}
			elements.forEach(this::doDraw);
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
	

	private void layout() {
		if(vp != null ) {
			/* Available space for whole indicator line. This is the width of the viewport,
			 * less <code>columns - 1</code> for the inter-element gap */
			var available = vp.getColumns() - (Math.max(0, elements.size() - 1)) - 1;
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
