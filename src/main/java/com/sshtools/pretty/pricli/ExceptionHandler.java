package com.sshtools.pretty.pricli;

import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.function.Supplier;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		var report = new AttributedStringBuilder();
		report.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		report.append(msg);
		report.style(AttributedStyle.DEFAULT.foregroundDefault());
		if(verboseExceptions.get()) {
			Throwable nex = ex;
			int indent = 0;
			while(nex != null) {
				report.append(System.lineSeparator());
				if(indent > 0) {
					report.append(String.format("%" + ( 8 + ((indent - 1 )* 2) ) + "s", ""));
					report.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
					report.append(nex.getMessage() == null ? "No message." : nex.getMessage());
					report.style(AttributedStyle.DEFAULT.foregroundDefault());
					report.append(System.lineSeparator());
				}
				
				for(var el : nex.getStackTrace()) {
					report.append(System.lineSeparator());
					report.append(String.format("%" + ( 8 + (indent * 2) ) + "s", ""));
					report.append("at ");
					if(el.getModuleName() != null) {
						report.append(el.getModuleName());
						report.append('/');
					}
					report.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
					report.append(el.getClassName());
					report.append('.');
					report.append(el.getMethodName());
					report.style(AttributedStyle.DEFAULT.foregroundDefault());
					if(el.getFileName() != null) {
						report.append('(');
						report.append(el.getFileName());
						if(el.getLineNumber() > -1) {
							report.append(':');
							report.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
							report.append(String.valueOf(el.getLineNumber()));
							report.style(AttributedStyle.DEFAULT.foregroundDefault());
							report.append(')');
						}
					}
				}
				indent++;
				nex = nex.getCause();
			}
		}
		report.println(terminal);
		terminal.flush();
	}

}
