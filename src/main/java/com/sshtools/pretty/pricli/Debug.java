package com.sshtools.pretty.pricli;

import java.util.concurrent.Callable;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.sshtools.terminal.emulation.DefaultViewport;
import com.sshtools.terminal.emulation.buffer.LineData;
import com.sshtools.terminal.emulation.emulator.DECEmulator;
import com.sshtools.terminal.emulation.emulator.DECPage;

import javafx.application.Platform;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "debug", 
         aliases = {"d"},
         footer = "%nAliases: d",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Debug actions.", 
         subcommands = {
			 Debug.Pack.class, 
			 Debug.Emulator.class,
			 Debug.Viewport.class,
			 Debug.Open.class
			})
public class Debug implements Callable<Integer> {

	@ParentCommand
	private DebugCommands parent;

	
	@Spec
	private CommandSpec spec;

	@Override
	public Integer call() throws Exception {
		spec.commandLine().usage(parent.cli().jline().writer(), Ansi.ON);
		return 0;
		


		//
		// DEBUG
		//
		// TODO move this somewhere else
//		if (control && shift && keyCode == VDUKeyEvent.F12) {
//			terminal.dumpStatus();
//		}
//		if (control && shift && keyChar == 'G') {
//			LOG.debug("Slow mode");
//			DECEmulator.setDebugDelay(DECEmulator.getDebugDelay() + 10);
//			return;
//		} else if (control && shift && keyChar == 'H') {
//			LOG.debug("Normal mode");
//			DECEmulator.setDebugDelay(0);
//			return;
		
//		} else if (control && shift && keyChar == 'I') {
//			terminal.onDebugModeRequest.accept(true);
//			LOG.debug("Display, emulation and buffer debug on");
//			return;
//		} else if (control && shift && keyChar == 'J') {
//			LOG.debug("Display, emulation and buffer debug off");
//			terminal.onDebugModeRequest.accept(false);
//			return;
//		}
	}

	@Command(name = "pack", aliases = {"p"}, usageHelpAutoWidth = true, description = "Pack the window.")
	public final static class Pack implements Callable<Integer> {

		@ParentCommand
		private Debug parent;
		
		@Override
		public Integer call() throws Exception {
			Platform.runLater(() -> parent.parent.tty().ttyContext().stage().sizeToScene());
			parent.parent.cli().result("Window packed");
			return 0;
		}

	}
	
	@Command(name = "open", aliases = {"o"}, usageHelpAutoWidth = true, description = "Parse argument string as if called remotely.")
	public final static class Open implements Callable<Integer> {

		@ParentCommand
		private Debug parent;
		
		@Parameters
		private String[] args;
		
		@Override
		public Integer call() throws Exception {
			parent.parent.ttyContext().getContainer().open(args == null ? new String[0] : args);
			return 0;
		}

	}


	@Command(name = "viewport", aliases = {"v", "vp"}, usageHelpAutoWidth = true, description = "Show information about the state of the viewport.")
	public final static class Viewport implements Callable<Integer> {

		@ParentCommand
		private Debug parent;
		
		@Override
		public Integer call() throws Exception {
			var jline = parent.parent.cli().jline();
			var tty = parent.parent.tty();
			var dpy = tty.terminal();
			var emu = (DECEmulator<?>)dpy.getViewport(); 
			var page = emu.getPage();
			var bufferData = page.data();
			for (var r = 0; r < page.height(); r++) {
				var row = bufferData.get(r);
				printRow(jline, r, row);
			}
			jline.writer().println();

			return 0;
		}

		private void printRow(Terminal jline, int no, LineData row) {
			var as = new AttributedStringBuilder();
			
			as.style(AttributedStyle.BOLD);
			as.append(String.format("%07d", no));
			as.style(AttributedStyle.BOLD_OFF);
			as.append(": ");
			
			var attrs = row.getLineAttributes();
			as.append(" attrs=0x");
			as.append(String.format("%02x", attrs));
			as.append(" soft=");
			as.append(String.valueOf((attrs & com.sshtools.terminal.emulation.Viewport.LINE_SOFT_WRAPPED) != 0));
			as.append(" hard=");
			as.append(String.valueOf((attrs & com.sshtools.terminal.emulation.Viewport.LINE_HARD_BREAK) != 0));
			as.append(" size=0x");
			as.append(String.format("%02x", (byte) (attrs & DefaultViewport.LINE_SIZE_MASK)));
			
			as.println(jline);
		}

	}

	@Command(name = "emulator", aliases = {"e", "em"}, usageHelpAutoWidth = true, description = "Show information about the state of the emulator.")
	public final static class Emulator implements Callable<Integer> {

		@ParentCommand
		private Debug parent;
		
		@Override
		public Integer call() throws Exception {
			var jline = parent.parent.cli().jline();
			var tty = parent.parent.tty();
			var dpy = tty.terminal();
			var emu = (DECEmulator<?>)dpy.getViewport(); 
			var page = emu.getPage();
			var modes = emu.getModes();
			var colors = emu.getColors();

			printTitle(jline, "Display");
			printRow(jline, "Character Height", String.valueOf(dpy.getFontManager().getDefault().charHeight()));
			printRow(jline, "Character Width", String.valueOf(dpy.getFontManager().getDefault().charWidth()));
			printRow(jline, "Character Decent", String.valueOf(dpy.getFontManager().getDefault().charDescent()));
			jline.writer().println();

			printTitle(jline, "Colors");
			printRow(jline, "Default Background", String.valueOf(colors.getBG().toRGBAHTMLColor()));
			printRow(jline, "Default Foreground", String.valueOf(colors.getFG().toRGBAHTMLColor()));
			jline.writer().println();
			
			printTitle(jline, "Emulator");
			printRow(jline, "Size", emu.getColumns() + " x " + emu.getRows());
			printRow(jline, "Display Rows", String.valueOf(emu.getDisplayRows()));
			printRow(jline, "Viewport End", String.valueOf(emu.getViewportEnd()));
			printRow(jline, "Character Set", String.valueOf(emu.getCharacterSet()));
			printRow(jline, "Screen Base", String.valueOf(emu.getViewportStart()));
			printRow(jline, "Cursor Style", String.valueOf(emu.getCursorStyle()));

			printPage(jline, "", page);
			modes.getAlternatePage().ifPresent(pg -> printPage(jline, "Alt. ", pg));

			jline.writer().println();
			printTitle(jline, "Modes");
			printRow(jline, "Allow Wide Mode", String.valueOf(modes.isAllowWideMode()));
			printRow(jline, "Alternate Screen", String.valueOf(modes.isAlternateBuffer()));
			printRow(jline, "Alternate Scroll Mode", String.valueOf(modes.isAlternateScrollMode()));
			printRow(jline, "Application Cursor Key", String.valueOf(modes.isApplicationCursorKeys()));
			printRow(jline, "Application Keypad", String.valueOf(modes.isApplicationKeypadMode()));
			printRow(jline, "Auto Repeat", String.valueOf(modes.isAutoRepeat()));
			printRow(jline, "Bracketed Paste", String.valueOf(modes.isBracketedPaste()));
			printRow(jline, "Cursor Blink", String.valueOf(modes.isCursorBlink()));
			printRow(jline, "Cursor On", String.valueOf(modes.isCursorOn()));
			printRow(jline, "Focus In/Focus Out Events", String.valueOf(modes.isFocusInFocusOutEvents()));
			printRow(jline, "Insert Mode", String.valueOf(modes.isInsertMode()));
			printRow(jline, "Keyboard Action", String.valueOf(modes.isKeyboardAction()));
			printRow(jline, "Left/Right Margin Mode", String.valueOf(modes.isLeftRightMarginMode()));
			printRow(jline, "Special Modifiers", String.valueOf(modes.isSpecialModifiers()));
			printRow(jline, "Light Background", String.valueOf(modes.isLightBackground()));
			printRow(jline, "Local Echo", String.valueOf(modes.isLocalEcho()));
			printRow(jline, "National Character Set", String.valueOf(modes.isNationalCharacterSet()));
			printRow(jline, "Origin Mode", String.valueOf(modes.isOriginMode()));
			printRow(jline, "Print Form Feed", String.valueOf(modes.isPrintFormFeed()));
			printRow(jline, "Reverse Wraparound", String.valueOf(modes.isReverseWrapAround()));
			printRow(jline, "Select Rectangular Area", String.valueOf(modes.isSelectRectangularArea()));
			printRow(jline, "Send CRLF", String.valueOf(modes.isSendCRLF()));
			printRow(jline, "Show Cursor", String.valueOf(modes.isShowCursor()));
			printRow(jline, "Smooth Scroll", String.valueOf(modes.isSmoothScroll()));
			printRow(jline, "Tite Inhibit", String.valueOf(modes.isTiteInhibit()));
			printRow(jline, "VT52 Mode", String.valueOf(modes.isVT52Mode()));
			printRow(jline, "Wide Mode", String.valueOf(modes.isWideMode()));
			printRow(jline, "Wrap Around", String.valueOf(modes.isWrapAround()));
			printRow(jline, "Clear Screen On Mode Change", String.valueOf(modes.isClearScreenOnModeChange()));
			printRow(jline, "Special Modifers", String.valueOf(modes.isSpecialModifiers()));
			printRow(jline, "Sixel Scrolling", String.valueOf(modes.isSixelScrolling()));
			
			return 0;
		}

		private void printPage(Terminal jline, String prefix, DECPage page) {
			jline.writer().println();
			printTitle(jline, prefix + "Page");
			printRow(jline, "Window Base", String.valueOf(page.windowBase()));
			printRow(jline, "Cursor", String.format("%d,%d", page.cursorX(), page.cursorY()));
			printRow(jline, "Margins Set", String.format("H: %s, V: %s", page.isHorizontalMarginSet(), page.isVerticalMarginSet()));
			printRow(jline, "Margin (T,R,B,L)", String.format("%d %d %d %d", page.getTopMargin(), page.getRightMargin(), page.getBottomMargin(), page.getLeftMargin()));

			jline.writer().println();
			var buf = page.data();
			printTitle(jline, prefix + "Buffer Data");
			printRow(jline, "Size", String.format("%d x %d", buf.getWidth(), buf.getSize()));
			printRow(jline, "Limit", String.valueOf(buf.getLimit()));
			printRow(jline, "Max Size", String.format("%d x %d", buf.getMaximumWidth(), buf.getDefaultMaximumSize()));
		}

		private void printTitle(Terminal jline, String title) {
			var as = new AttributedStringBuilder();
			as.style(new AttributedStyle().underline());
			as.append(title);
			as.style(new AttributedStyle().underlineOff());
			as.println(jline);
		}

		private void printRow(Terminal jline, String key, String val) {
			var as = new AttributedStringBuilder();
			
			as.style(AttributedStyle.BOLD);
			as.append(String.format("%-20s", key));
			as.style(AttributedStyle.BOLD_OFF);
			as.append(": ");
			as.append(val);
			as.println(jline);
		}

	}
}
