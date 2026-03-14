package com.sshtools.pretty.pricli.ssh;

import java.io.EOFException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;

import com.sshtools.client.SshClient;
import com.sshtools.client.sftp.SftpClient;
import com.sshtools.common.permissions.PermissionDeniedException;
import com.sshtools.common.sftp.SftpStatusException;
import com.sshtools.common.ssh.SshException;
import com.sshtools.pretty.pricli.LocalFileCommand;
import com.sshtools.pretty.ssh.SshInstance;
import com.sshtools.pretty.ssh.SshProtocol;

import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command
public abstract class SftpCommand extends LocalFileCommand {
	@ParentCommand
	protected SshCommands parent;
	
	protected SftpCommand() {
		super();
	}
	
	protected SftpCommand(FilenameCompletionMode mode) {
		super(mode);
	}
	
	protected SshInstance getSshInstance() {
		var sshProto = (SshProtocol)parent.cli().tty().protocol();
		return sshProto.getInstance();
	}

	protected SftpClient sftpClient() {
		return getSshInstance().sftp();
	}

	protected SshClient sshClient() {
		return getSshInstance().client();
	}

	protected Optional<String> expandRemoteSingleOr(Optional<String> path) throws SshException, SftpStatusException, IOException, PermissionDeniedException {
		if(path.isEmpty()) {
			return path;
		}
		else
			return Optional.of(expandRemoteSingle(path.get()));
	}

	protected String expandRemoteSingle(Optional<String> path) throws SshException, SftpStatusException, IOException, PermissionDeniedException {
		var expandRemoteSingleOr = expandRemoteSingleOr(path);
		if(expandRemoteSingleOr.isPresent())
			return expandRemoteSingleOr.get();
		else {
			return sftpClient().pwd();
		}
	}

	protected String expandRemoteSingle(String path) throws SshException, SftpStatusException, IOException, PermissionDeniedException {
		var l = new ArrayList<String>();
		expandRemoteAndDo((fp) -> {
			if(!l.isEmpty())
				throw new EOFException();
			l.add(fp);
		}, true, path);
		if(l.isEmpty())
			return path;
		else
			return l.get(0);
	}
	
	protected String[] expandRemoteArray(String... paths)  throws SshException, SftpStatusException, IOException, PermissionDeniedException {
		var l = new ArrayList<String>();
		for(var path : paths) {
			expandRemoteAndDo((fp) -> {
				l.add(fp);
			}, true, path);
		}
		return l.toArray(new String[0]);
	}

	protected void expandRemoteAndDo(FileOp op, boolean recurse, String... paths) throws SshException, SftpStatusException, IOException, PermissionDeniedException  {

		for(var path : paths) {
			
			path = expandSpecialRemotePath(path);
			path = Path.of(path).normalize().toString();
			
			if(path.toString().equals("..")) {
				var parentFile = sftpClient().getCurrentWorkingDirectory().getParentFile();
				var parentPath = parentFile == null ? null : parentFile.getAbsolutePath();
				path = parentPath == null ? "/" : parentPath;
			}

			var absolute = path.startsWith("/");
			var root = absolute ? "/" : sftpClient().pwd();
			var resolved = root;
			var pathParts = ( path.startsWith("/") ? path.substring(1) : path ).split("/");
			var pathCount = pathParts.length;
			
			for(int i = 0 ; i < pathCount; i++) {
				var pathPart = pathParts[i];
				var matches = 0;
				var matcher = FileSystems.getDefault().getPathMatcher("glob:" + pathPart);
				
				for(var it =  sftpClient().lsIterator(resolved); it.hasNext(); ) {
					var pathPartPath = it.next();
					var fullPath = resolved;
					var filename = pathPartPath.getFilename();
					if(filename.equals(".") || filename.equals("..")) {
						continue;
					}
					if(matcher.matches(Path.of(filename))) {
						fullPath += "/" + filename;
						matches++;
						if(i == pathCount -1) {
							try {
								op.op(fullPath);
							} catch(EOFException ee) {
								return;
							} catch (Exception e) {
								if(e instanceof SftpStatusException)
									throw (SftpStatusException)e;
								else if(e instanceof SshException)
									throw (SshException)e;
								else
									throw new SshException(e);
							}
						}
					}
				}
				
				if(!recurse || matches == 0)
					break;
				
				resolved = resolved + "/" + pathPart;
			}
		}
	}

	protected int getUID(String username) throws IOException {

		SshClient ssh = sshClient();
		try {
			return Integer.parseInt(ssh.executeCommand(String.format("id -u %s", username)));
		} catch (NumberFormatException e) {
			throw new IOException("Could not determine uid from username");
		}
	}

	protected int getGID(String groupname) throws IOException {

		SshClient ssh = sshClient();
		try {
			return Integer.parseInt(ssh.executeCommand(String.format("id -u %s", groupname)));
		} catch (NumberFormatException e) {
			throw new IOException("Could not determine uid from username");
		}
	}

	String expandSpecialRemotePath(String path) throws SftpStatusException, SshException {
		if(path.toString().startsWith("~/") || path.toString().startsWith("~\\")) {
			return sftpClient().getDefaultDirectory() + path.toString().substring(1);
		}
		else
			return path;
	}
}
