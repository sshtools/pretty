package com.sshtools.pretty.pricli;

import java.util.concurrent.Callable;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.sshtools.terminal.emulation.emulator.DECEmulator;
import com.sshtools.terminal.emulation.emulator.DECPage;

import javafx.application.Platform;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(name = "debug", aliases = {"d"}, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Debug actions.", subcommands = {Debug.Pack.class, Debug.Emulator.class})
public class Debug implements Callable<Integer> {

	@ParentCommand
	private Pricli.PricliCommands parent;

	@Override
	public Integer call() throws Exception {
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

	@Command(name = "pack", aliases = {"p"}, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Pack the window.")
	public final static class Pack implements Callable<Integer> {

		@ParentCommand
		private Debug parent;
		
		@Override
		public Integer call() throws Exception {
			Platform.runLater(() -> parent.parent.tty().getTTYContext().stage().sizeToScene());
			parent.parent.cli().result("Window packed");
			return 0;
		}

	}

	@Command(name = "emulator", aliases = {"e", "em"}, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Show information about the state of the emulator.")
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
			printRow(jline, "Max Size", String.format("%d x %d", buf.getMaximumWidth(), buf.getMaximumSize()));
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
