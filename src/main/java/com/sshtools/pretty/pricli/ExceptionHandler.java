package com.sshtools.pretty.pricli;

import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.function.Supplier;

import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.pretty.Strings;
import com.sshtools.pretty.TTY;

import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;

/**
 * Captures exceptions thrown by command handlers and prints them to the
 * terminal in a user-friendly way.
 */
public class ExceptionHandler implements IExecutionExceptionHandler {
	static Logger LOG = LoggerFactory.getLogger(TTY.class);
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(ExceptionHandler.class.getName());
	
	private final Terminal terminal;
	private final Supplier<Boolean> verboseExceptions;

	public ExceptionHandler(Terminal terminal, Supplier<Boolean> verboseExceptions) {
		this.terminal = terminal;
		this.verboseExceptions = verboseExceptions;
	}

	@Override
	public int handleExecutionException(Exception ex, CommandLine commandLine, ParseResult parseResult)
			throws Exception {
		LOG.error("User target exception.", ex);
		var msg = ex.getMessage() == null ? "An unknown error occured." : ex.getMessage();
		if (ex instanceof IllegalStateException ise && ise.getCause() != null
				&& ise.getCause() instanceof Exception exe) {
			ex = exe;
		} else if (ex instanceof UncheckedIOException ise && ise.getCause() != null) {
			ex = ise.getCause();
		}

		if (ex instanceof EndOfFileException) {
			msg = MessageFormat.format(RESOURCES.getString("endOfFileException"),
					ex.getMessage() == null ? "" : ex.getMessage());
		} else if (ex instanceof UnknownHostException) {
			msg = MessageFormat.format(RESOURCES.getString("unknownHostException"),
					ex.getMessage() == null ? "" : ex.getMessage());
		} else if (ex instanceof UserInterruptException || ex instanceof InterruptedIOException) {
			msg = MessageFormat.format(RESOURCES.getString("userInterruptException"),
					ex.getMessage() == null ? "" : ex.getMessage());
		}

		printExceptionAndMessage(ex, msg);
		return 1;
	}

	public void printExceptionAndMessage(Exception ex, String msg) {
		var report = Strings.ansiExceptionString(verboseExceptions.get(), ex, msg);
		report.println(terminal);
		terminal.flush();
	}

}
