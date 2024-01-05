package com.sshtools.pretty.pricli;

import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.function.Supplier;

import org.jline.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.pretty.Strings;
import com.sshtools.pretty.TTY;

import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;

public class ExceptionHandler implements IExecutionExceptionHandler {
	static Logger LOG = LoggerFactory.getLogger(TTY.class);
	
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
		if(ex instanceof UnknownHostException) {
			msg = MessageFormat.format("Could not resolve hostname {0}: Name or service not known.", ex.getMessage());
		}
		printExceptionAndMessage(ex, msg);
		return 0;
	}

	public void printExceptionAndMessage(Exception ex, String msg) {
		var report = Strings.ansiExceptionString(verboseExceptions.get(), ex, msg);
		report.println(terminal);
		terminal.flush();
	}

}
