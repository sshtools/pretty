package com.sshtools.pretty.pricli;

import java.text.MessageFormat;
import java.util.Calendar;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.sshtools.pretty.LineStringBuilder;
import com.sshtools.pretty.LineStyle;
import com.sshtools.pretty.Pretty;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(name = "about", aliases = {"ab"}, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Show information about Pretty.")
public class About implements Callable<Integer> {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(About.class.getName());
	
	@ParentCommand
	private Pricli.PricliCommands parent;
	
	@Override
	public Integer call() throws Exception {

		var trm = parent.cli().jline();
		var wtr = trm.writer();

		var tsb = new LineStringBuilder();
		tsb.style(LineStyle.DOUBLE_WIDTH_AND_HEIGHT);
        tsb.append(RESOURCES.getString("title"));
        tsb.toAttributedString().println(trm);
		
		var dsb = new AttributedStringBuilder();
        dsb.style(new AttributedStyle().faint());
        dsb.append(RESOURCES.getString("description"));
        dsb.toAttributedString().println(trm);
		
		var asb = new AttributedStringBuilder();
        asb.style(new AttributedStyle().bold());
        asb.append(MessageFormat.format(RESOURCES.getString("version"), String.join(" ", Pretty.getVersion())));
        asb.toAttributedString().println(trm);
        
        wtr.println();

		var csb = new AttributedStringBuilder();
        csb.style(new AttributedStyle().underline());
        csb.append(MessageFormat.format(RESOURCES.getString("copyright"),
				String.valueOf(Calendar.getInstance().get(Calendar.YEAR))));
        csb.toAttributedString().println(trm);
        
        wtr.println();
		return 0;
	}
}
