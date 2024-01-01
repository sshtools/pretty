package com.sshtools.pretty.pricli.ssh;

import static com.sshtools.common.util.IOUtils.toByteSize;

import java.util.Optional;
import java.util.ResourceBundle;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "df", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Display current remote directory")
public class Df extends SftpCommand {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Df.class.getName());
	
	@Option(names = "-H", description = "human readable sizes")
	private boolean humanReadable;
	
	@Option(names = "-i", description = "show inodes instead of space.")
	private boolean inodes;

	@Parameters(index = "0", arity="0..1", paramLabel="PATH", description = "path of directory to list")
	private Optional<String> path;
	
	public Df() {
		super(FilenameCompletionMode.DIRECTORIES_REMOTE);
	}
	
	@Override
	public Integer call() throws Exception {
		var term = parent.cli().jline();
		var wtr = term.writer();
		var bldr = new AttributedStringBuilder();

		bldr.style(new AttributedStyle().underline());
		if(inodes) {
			bldr.append(RESOURCES.getString("inodes"));
		}
		else {
			bldr.append(RESOURCES.getString("blocks"));
		}
		bldr.style(new AttributedStyle().underlineOff());
		bldr.print(term);
		
		var stat = sftpClient().statVFS(expandRemoteSingle(path));
		if(inodes) {
			wtr.format(String.format("%11d %11d %11d %11d %10d%%%n", 
						stat.getINodes(), stat.getINodes() - stat.getFreeINodes(), 
						stat.getAvailINodes(), 0, 0));
		}
		else {
			if(humanReadable) {
				wtr.format(String.format("%12s %12s %12s %12s %11d%%%n", 
							toByteSize(stat.getSize()), 
							toByteSize(stat.getUsed()), 
							toByteSize(stat.getAvailForNonRoot()),
							toByteSize(stat.getAvail()),    
							stat.getCapacity()));
			}
			else { 
				wtr.format(String.format("%12d %12d %12d %12d %11d%%%n", 
							stat.getSize(), 
							stat.getUsed(), 
							stat.getAvailForNonRoot(),
							stat.getAvail(),    
							stat.getCapacity()));
			}
		}
		return 0;
	}
}
