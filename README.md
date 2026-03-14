# Pretty

A deceptively powerful modern terminal emulator

<img width="600" height="487" alt="pretty" src="https://github.com/user-attachments/assets/c2419506-8120-4f5f-9b66-6626e06b3949" />

## Features

 * Cross platform, for Linux, Mac OS and Windows.
 * Full emulation of ANSI, VT52, VT100, VT200, VT300 and parts of VT400 and VT500. Also mostly XTERM compatible. 
 * Simple, minimal user interface for day to day usage, or powerful *Sub-shell* for advanced usage.
 * Supports Kitty, iTerm and SIXEL graphics.
 * Unambiguous keyboard handling.
 * Transparency.
 * Use for local shells, or remote connections over the built in SSH and Serial support.
 * Desktop notifications via either Iterm2 or Kitty protocols.
 * File transfers using SFTP, Iterm2, XMODEM, YMODEM, ZMODEM, or Kitty protocols (WIP). 
 * Tabs or Windowed user interface with infinite viewport splitting. Tabs can be renamed and coloured for easy identification.
 * Unlimited scrollback buffer.
 * Customisable themes, actions, fonts, all with simple *.ini* type files.
 * Full port and domain socket forwarding support over SSH.
 
## Get Pretty

There are alpha builds available in the [releases](https://github.com/sshtools/pretty/releases) page. 

Once installed, you can get update inside Pretty itself. Currently you will need to make sure you select the `Continuous Developer Builds` update channel to get developer updates until the early access or stable version is release..

Or, just clone this repository and run `mvn exec:java` (you will need Java 25 and Maven installed).

# Be Pricli

*Pricli* is the "sub-shell". While a lot of common configuration is available in the *Options* user interface, for advanced features you will need to use Pricli.

Just about everything that happens in Pretty can be trigger with a command, and everything that can be configured can be done so from there too. 

 * Make connections to servers or serials ports with advanced options.
 * Initial file transfers or other file management operations.
 * Configure terminal behaviour.
 * Perform terminal related actions such as clipboard operations, selection manipulation, scrolling and more.
 * Command history (use arrow keys as normal).
 * Create new tabs and windows
 * Debugging and inspection aids.

Hit `Alt+/`, and a small window will popup either above or below your cursor depending on where it is.

For example, upon opening Pretty you are shown your default console. You want to connect to a remote SSH server, so you type hit `Alt+/` and type ..

```
→ ssh joe.b@nas.mydomain.com
Connecting to joe.b@nas.mydomain.com:22
Password: ●●●●●●●●●●●
```

Pricli will the disappear, and your terminal will now be connected to the remote server.

Now, say you want to upload a file to the server. Hit `Alt+/` again, and you will see Pricli has been extended with additional SSH related commands, and the prompt has changed.

```
ssh://joe.b@nas.mydomain.com → 
```

*At any time type `help` for a full list of available commands for the current connection*

To transfer a file, first `cd` to the remote directory where you want to place the files, then use the `put` command with the path to the local file.

```
ssh://joe.b@nas.mydomain.com → cd /srv/myfiles
ssh://joe.b@nas.mydomain.com → put /home/joe/ForUploading/test-plan-1.txt
```

## Known Issues

There are a number ...

 * Limited Scrollback buffer implementation is a bit slow.
 * Sixel images are not erased when characters drawn over them.
 * Wide mode switching unreliable.
 * Reflowing is incomplete
    
## Goals

Pretty is just coming out of alpha stage. Before final release, the following will be available.

 * Shell Integration (as Kitty and iTerm)
 * More theming options
 * Tektronic and REGIS graphics.
 * Further VT420, VT540. 
 
## History

Pretty is based on Terminal Components 4, Jadaptive's multi-toolkit terminal emulation library. *TC4* is an almost complete rewrite, intended to make it easier to move forward and implement modern terminal protocols and techniques. Pretty is effectively a brand new reference implementation of TC4, whilst being a great terminal in it's own right. See the home page for TC4 for more on it's history. 

Or, take a look at UniTTY, our other desktop remote access application also based on Terminal Components.

## Credits

 * Shell provided by the great [https://github.com/jline/jline3](JLine)
 * Command line parsing by [https://picocli.info/](Picocli)
 * SSH support by [https://jadaptive.com](JADAPTIVE)
