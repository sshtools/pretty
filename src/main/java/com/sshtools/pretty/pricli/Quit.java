package com.sshtools.pretty.pricli;

import static com.sshtools.pretty.pricli.Styling.styled;

import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import org.jline.reader.impl.LineReaderImpl;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(name = "quit", aliases = {"q"}, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Quit all terminals.")
public class Quit implements Callable<Integer> {

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Quit.class.getName());

	@Option(names = {"-f", "--force"}, description="Force quit without question.")
	private boolean force;
	
	@ParentCommand
	private Pricli.PricliCommands parent;
	
	@Override
	public Integer call() throws Exception {
		if(force) {
			System.exit(0);
		}
		else {
			var jline = parent.cli().jline();
			styled(RESOURCES.getString("confirm")).print(jline);
			jline.flush();
			var reply = ((LineReaderImpl)parent.cli().reader()).readCharacter();
			if(reply == -1) {
				return 1;
			}
			if(Character.toLowerCase((char)reply)== RESOURCES.getString("yes").charAt(0)) {
				System.exit(0);
			}
			else {
				jline.writer().println();
			}
		}
		return 0;
	}
}
