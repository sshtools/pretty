package com.sshtools.pretty.pricli;

import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.sshtools.common.util.IOUtils;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(name = "images", 
         aliases = {"imgs"},
         footer = "%nAliases: imgs",
         usageHelpAutoWidth = true, 
         mixinStandardHelpOptions = true, 
         description = "Shows the status of the image cache.")
public class Images implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Images.class.getName());

	@ParentCommand
	private DebugCommands parent;
	
	@Override
	public Integer call() throws Exception {
		var jline = parent.cli().jline();
		var is = parent.tty().terminal().getImageSupport();
		
		var as = new AttributedStringBuilder();
		
		as.style(AttributedStyle.BOLD);
		as.append(RESOURCES.getString("maxMemory"));
		as.style(AttributedStyle.BOLD_OFF);
		as.append(": ");
		as.append(IOUtils.toByteSize(is.maxMemory(), 2));
		as.println(jline);
		
		as = new AttributedStringBuilder();
		as.style(AttributedStyle.BOLD);
		as.append(RESOURCES.getString("freeMemory"));
		as.style(AttributedStyle.BOLD_OFF);
		as.append(": ");
		as.append(IOUtils.toByteSize(is.freeMemory(), 2));
		as.println(jline);
		
		as = new AttributedStringBuilder();
		as.style(AttributedStyle.BOLD);
		as.append(RESOURCES.getString("usedMemory"));
		as.style(AttributedStyle.BOLD_OFF);
		as.append(": ");
		as.append(IOUtils.toByteSize(is.usedMemory(), 2));
		as.println(jline);
		
		as = new AttributedStringBuilder();
		as.style(AttributedStyle.BOLD);
		as.append(RESOURCES.getString("maxImages"));
		as.style(AttributedStyle.BOLD_OFF);
		as.append(": ");
		as.append(String.valueOf(is.maxImages()));
		as.println(jline);
		
		as = new AttributedStringBuilder();
		as.style(AttributedStyle.BOLD);
		as.append(RESOURCES.getString("freeImages"));
		as.style(AttributedStyle.BOLD_OFF);
		as.append(": ");
		as.append(String.valueOf(is.freeImages()));
		as.println(jline);
		
		as = new AttributedStringBuilder();
		as.style(AttributedStyle.BOLD);
		as.append(RESOURCES.getString("usedImages"));
		as.style(AttributedStyle.BOLD_OFF);
		as.append(": ");
		as.append(String.valueOf(is.usedImages()));
		as.println(jline);
		
		return 0;
	}
}
