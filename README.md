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
 * File transfers using SSH, Iterm2 or Kitty protocols (WIP).
 * Tabs or Windowed user interface.
 
## Get Pretty

There are alpha builds available in the [releases](https://github.com/sshtools/pretty/releases) page. 

Once installed, you can get update inside Pretty itself. Currently you will need to make sure you select the `Continuous Developer Builds` update channel to get developer updates until the early access or stable version is release..

Or, just clone this repository and run `mvn exec:java` (you will need Java 25 and Maven installed).

## Known Issues

There are a number ...

 * Scrollback buffer implementation is a bit slow.
 * Sixel images are not erased when characters drawn over them.
 * Wide mode switching unreliable.
    
## Goals

Pretty is just coming out of alpha stage. Before final release, the following will be available.

 * Shell Integration (as Kitty and iTerm)
 * Lots more SSH options including Jadaptive's Push-SFTP for hyperfast file transfers.
 * More theming options
 * Tektronic and REGIS graphics.
 * Further VT420, VT540. 
 
## History

Pretty is based on Terminal Components 4, Jadaptive's multi-toolkit terminal emulation library. *TC4* is
an almost complete rewrite, intended to make it easier to move forward and implement modern terminal 
protocols and techniques. Pretty is effectively a brand new reference implementation of TC4, 
whilst being a great terminal in it's own right. See the home page for TC4 for more on it's history. 

Or, take a look at UniTTY, our other desktop remote access application also based on Terminal Components.

 
