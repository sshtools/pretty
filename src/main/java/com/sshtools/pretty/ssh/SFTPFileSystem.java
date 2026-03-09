package com.sshtools.pretty.ssh;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.attribute.FileTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.cryptomator.jfuse.api.DirFiller;
import org.cryptomator.jfuse.api.Errno;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.FuseConfig;
import org.cryptomator.jfuse.api.FuseConnInfo;
import org.cryptomator.jfuse.api.FuseOperations;
import org.cryptomator.jfuse.api.Stat;
import org.cryptomator.jfuse.api.Statvfs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.client.sftp.SftpClient;
import com.sshtools.client.sftp.SftpHandle;
import com.sshtools.common.sftp.PosixPermissions.PosixPermissionsBuilder;
import com.sshtools.common.sftp.SftpFileAttributes;
import com.sshtools.common.sftp.SftpStatusException;
import com.sshtools.common.ssh.SshException;
import com.sshtools.common.util.UnsignedInteger64;


public class SFTPFileSystem implements FuseOperations {
	private static final Logger LOG = LoggerFactory.getLogger(SFTPFileSystem.class);
	private final AtomicLong dirHandleGen = new AtomicLong(1L);
	private final Errno errno;
	private final AtomicLong fileHandleGen = new AtomicLong(1L);
	private final SftpClient mount;
	private final Map<Long, SftpHandle> openDirs = new ConcurrentHashMap<>();
	private final Map<Long, SftpHandle> openFiles = new ConcurrentHashMap<>();
	private final String root;
	private final static int RENAME_NOREPLACE = 1;

	public SFTPFileSystem(String root, SftpClient mount, Errno errno) {
		this.errno = errno;
		this.mount = mount;
		this.root = "/".equals(root) ? null : root;
	}
	
	public String root() {
		return root;
	}
	
	public SftpClient mount() {
		return mount;
	}

	@Override
	public int create(String rpath, int mode, FileInfo fi) {
		var path = resolve(rpath);
		LOG.trace("create {}", path);
		return ioCall(() -> {
			var fc = mount.openFile(path, fi.getOpenFlags());
			if(mode > 0) {
				mount.chmod(PosixPermissionsBuilder.create().
						fromBitmask(mode).
						build(), path);
			}
			var fh = fileHandleGen.incrementAndGet();
			fi.setFh(fh);
			openFiles.put(fh, fc);
			return 0;
		});
	}

	@Override
	public void destroy() {
		LOG.info("destroy()");
		try {
			mount.close();
		} catch (IOException e) {
			LOG.error("Failed to close cleanly.", e);
		}
	}

	@Override
	public Errno errno() {
		return errno;
	}

	@Override
	public int getattr(String rpath, Stat stat, FileInfo fi) {
		var path = resolve(rpath);
		if(LOG.isDebugEnabled()) {
			LOG.debug("getattr() {}", path);
		}
		return ioCall(() -> {
			setStat(stat, mount.stat(path));
			return 0;
		});
	}

	@Override
	public void init(FuseConnInfo conn, FuseConfig cfg) {
		if(LOG.isDebugEnabled()) {
			LOG.info("init() {}.{}", conn.protoMajor(), conn.protoMinor());
		}
	}

	@Override
	public int mkdir(String rpath, int mode) {
		var path = resolve(rpath);
		if(LOG.isDebugEnabled()) {
			LOG.debug("mkdir {}", path);
		}
		return ioCall(() -> {
			mount.mkdir(path);
			mount.chmod(PosixPermissionsBuilder.create().
					fromBitmask(mode).
					build(), path);
			return 0;
		});
	}

	@Override
	public int open(String rpath, FileInfo fi) {
		var path = resolve(rpath);
		LOG.trace("open {}", path);
		return ioCall(() -> {
			var fc = mount.openFile(path, fi.getOpenFlags());
			var fh = fileHandleGen.incrementAndGet();
			fi.setFh(fh);
			openFiles.put(fh, fc);
			return 0;
		});
	}

	@Override
	public int opendir(String rpath, FileInfo fi) {
		var path = resolve(rpath);
		if(LOG.isDebugEnabled()) {
			LOG.debug("opendir {}", path);
		}
		return ioCall(() -> {
			var fc = mount.openDirectory(path);
			var fh = dirHandleGen.incrementAndGet();
			fi.setFh(fh);
			openDirs.put(fh, fc);
			return 0;
		});
	}

	@Override
	public int read(String rpath, ByteBuffer buf, long size, long offset, FileInfo fi) {
		var path = resolve(rpath);
		if(LOG.isTraceEnabled()) {
			LOG.trace("read {} at pos {}", path, offset);
		}
		var fc = openFiles.get(fi.getFh());
		if (fc == null) {
			return -errno.ebadf();
		}

		return ioCall(() -> {
			var read = 0;
			var toRead = (int) Math.min(size, buf.limit());
			while (read < toRead) {
				int r = fc.readFile(new UnsignedInteger64(offset + read), buf, 0, toRead - read);
				if (r == -1) {
					LOG.trace("Reached EOF");
					break;
				}
				read += r;
			}
			buf.flip();
			return read;
		});
	}

	@Override
	public int readdir(String rpath, DirFiller filler, long offset, FileInfo fi, int flags) {
		var path = resolve(rpath);
		if(LOG.isTraceEnabled()) {
			LOG.trace("readdir {} ({} [])", path, fi.getFh(), String.format("%04x", fi.getFh()));
		}
		return ioCall(() -> {
			openDirs.get(fi.getFh()).listChildren(entry -> {
				try {
					filler.fill(entry.getFilename(), st -> {;
						try {
							setStat(st, mount.stat(path));
						} catch (SshException | SftpStatusException  e) {
							throw new UncheckedIOException(new IOException(e));
						}
					});
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
			return 0;
		});
	}

	@Override
	public int release(String rpath, FileInfo fi) {
		var path = resolve(rpath);
		if(LOG.isDebugEnabled()) {
			LOG.debug("release {}", path);
		}
		var fc = openFiles.remove(fi.getFh());
		if (fc == null) {
			return -errno.ebadf();
		}

		return ioCall(() -> {
			fc.close();
			return 0;
		});
	}

	@Override
	public int releasedir(String rpath, FileInfo fi) {
		var path = resolve(rpath);
		if(LOG.isDebugEnabled()) {
			LOG.debug("releasedir {}", path);
		}
		return ioCall(() -> {
			var fh = openDirs.remove(fi.getFh());
			fh.close();
			return 0;
		});
	}

	@Override
	public int rename(String roldpath, String rnewpath, int flags) {
		var oldpath = resolve(roldpath);
		var newpath = resolve(rnewpath);
		if(LOG.isDebugEnabled()) {
			LOG.debug("rename {} {}", oldpath, newpath);
		}
		return ioCall(() -> {
			if((flags & RENAME_NOREPLACE) == 0 && mount.exists(newpath)) {
				mount.rm(newpath);
			}
			mount.rename(oldpath, newpath);
			return 0;
		});
	}

	@Override
	public int rmdir(String rpath) {
		var path = resolve(rpath);
		if(LOG.isDebugEnabled()) {
			LOG.debug("rmdir {}", path);
		}
		return ioCall(() -> {
			mount.rmdir(path);
			return 0;
		});
	}

	@Override
	public int statfs(String rpath, Statvfs statvfs) {
		var path = resolve(rpath);
		if(LOG.isDebugEnabled()) {
			LOG.debug("statfs() {}", path);
		}
		return ioCall(() -> {
			var stat = mount.statVFS(path);
			statvfs.setNameMax(stat.getMaximumFilenameLength());
			statvfs.setBsize(stat.getBlockSize());
			statvfs.setBlocks(stat.getBlocks());
			statvfs.setBfree(stat.getFreeBlocks());
			statvfs.setBavail(stat.getAvailBlocks());
			statvfs.setFrsize(stat.getFragmentSize());
			return 0;
		});
	}

	@Override
	public Set<Operation> supportedOperations() {
		return EnumSet.of(
		/*
		 * For libfuse 3 to ensure that the readdir operation runs in readdirplus mode,
		 * you have to add FuseOperations.Operation.INIT to the set returend by
		 * FuseOperations::supportedOperations method to the supported operations. An
		 * implementation of init is not necessary.
		 */
//			Operation.INIT, 
			Operation.DESTROY, 
			Operation.GET_ATTR, 
			Operation.OPEN, 
			Operation.OPEN_DIR,  
			Operation.RELEASE,
			Operation.MKDIR, 
			Operation.CHMOD,
			Operation.RMDIR, 
			Operation.RENAME,  
			Operation.READ,  
			Operation.WRITE,
			Operation.UNLINK, 
			Operation.RELEASE_DIR,
			Operation.READ_DIR,
			Operation.STATFS,
			Operation.CREATE
		);
	}

	@Override
	public int unlink(String rpath) {
		var path = resolve(rpath);
		if(LOG.isDebugEnabled()) {
			LOG.debug("unlink {}", path);
		}
		return ioCall(() -> {
			mount.rm(path);
			return 0;
		});
	}
	
	@Override
	public int write(String rpath, ByteBuffer buf, long count, long offset, FileInfo fi) {
		var path = resolve(rpath);
		LOG.trace("write {} at pos {}", path, offset);
		var fc = openFiles.get(fi.getFh());
		if (fc == null) {
			return -errno.ebadf();
		}

		return ioCall(() -> {
			var written = 0;
			var toWrite = (int) Math.min(count, buf.limit());
			int len;
			while (written < toWrite) {
				len = toWrite - written;
				fc.write(offset + written, buf, 0, len);
				written += len;
			}
			return written;
		});
	}

	private int ioCall(Callable<Integer> task) {
		try {
			return task.call();
		} catch(SftpStatusException sftpE) {
			switch(sftpE.getStatus()) {
			case SftpStatusException.SSH_FX_NO_SUCH_PATH:
			case SftpStatusException.SSH_FX_NO_SUCH_FILE:
				LOG.debug("ENOENT. ", sftpE);
				return -errno.enoent();
			case SftpStatusException.SSH_FX_PERMISSION_DENIED:
				LOG.debug("EACCES. ", sftpE);
				return -errno.eacces();
			case SftpStatusException.SSH_FX_INVALID_HANDLE:
				LOG.debug("EBADF. ", sftpE);
				return -errno.ebadf();
			case SftpStatusException.SSH_FX_FILE_ALREADY_EXISTS:
				LOG.debug("EEXIST. ", sftpE);
				return -errno.eexist();
			case SftpStatusException.SSH_FX_WRITE_PROTECT:
				LOG.debug("EROFS. ", sftpE);
				return -errno.erofs();
			case SftpStatusException.SSH_FX_NOT_A_DIRECTORY:
				LOG.debug("ENOTDIR. ", sftpE);
				return -errno.enotdir();
			case SftpStatusException.SSH_FX_DIR_NOT_EMPTY:
				LOG.debug("ENOTEMPTY. ", sftpE);
				return -errno.enotempty();
			case SftpStatusException.SSH_FX_FILE_IS_A_DIRECTORY:
				LOG.debug("EISDIR. ", sftpE);
				return -errno.eisdir();
			case SftpStatusException.SSH_FX_INVALID_FILENAME:
				LOG.debug("ENAMETOOLONG. ", sftpE);
				return -errno.enametoolong();
			case SftpStatusException.SSH_FX_INVALID_PARAMETER:
				LOG.debug("EINVAL. ", sftpE);
				return -errno.einval();
			case SftpStatusException.SSH_FX_BYTE_RANGE_LOCK_REFUSED:
				LOG.debug("ENOLCK. ", sftpE);
				return -errno.enolck();
			default:
				LOG.debug("EIO. ", sftpE);
				return -errno.eio();
			}
		} catch (IllegalArgumentException e) {
			LOG.debug("EINVAL. ", e);
			return -errno.einval();
		} catch (UnsupportedOperationException e) {
			if(LOG.isDebugEnabled())
				LOG.error("ENOSYS. ", e);
			else
				LOG.error("ENOSYS. {}", e.getMessage() == null ? "No message supplied." : e.getMessage());
			return -errno.enosys();
		} catch (FileAlreadyExistsException e) {
			LOG.debug("EEXIST. ", e);
			return -errno.eexist();
		} catch (NotDirectoryException e) {
			LOG.debug("ENOTDIR. ", e);
			return -errno.enotdir();
		} catch (FileNotFoundException | NoSuchFileException e) {
			if(LOG.isDebugEnabled()) {
				if(LOG.isTraceEnabled()) {
					LOG.error("ENOENT. ", e);
				}
				else {
					LOG.error("ENOENT. {}", e.getMessage());
				}
			}
			return -errno.enoent();
		} catch (UncheckedIOException | IOException e) {
			LOG.debug("EIO. ", e);
			return -errno.eio();
		} catch (Exception e) {
			return -errno.eio();
		}
	}

	private void setStat(Stat stat, SftpFileAttributes fstat) {
		stat.setPermissions(fstat.permissions().asPermissions());
		stat.setGid(fstat.gid());
		stat.setUid(fstat.uid());
		stat.setSize(fstat.size().longValue());

		if (fstat.isDirectory()) {
			stat.setModeBits(Stat.S_IFDIR);
			stat.setNLink((short) 2); // quick and dirty implementation. should really be 2 + subdir count
		} else {
			stat.setNLink((short) 1);
			stat.setModeBits(Stat.S_IFREG);
		}
		fstat.lastAccessTimeOr().map(FileTime::toInstant).ifPresent(stat.aTime()::set);
		fstat.lastModifiedTimeOr().map(FileTime::toInstant).ifPresent(stat.mTime()::set);
		fstat.createTimeOr().map(FileTime::toInstant).ifPresent(stat.cTime()::set);
	}
	
	private String resolve(String path) {
		if(root == null) {
			return path;
		}
		else if("/".equals(path)) {
			return root;
		}
		else if(path.startsWith("/")) {
			return root + path;
		}
		else {
			return root + "/" + path;
		}
	}
	
}
