package com.sshtools.pretty;

import static com.sshtools.jajafx.FXUtil.maybeQueue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sshtools.jajafx.JajaFXAppWindow;

public final class CustomCSS {

	private final Path managedFile;
	private final Configuration configuration;

	public CustomCSS(AppContext app) {
		
		configuration = app.getConfiguration();
		managedFile = configuration.dir().resolve("managed-styles.css");
		save();

		configuration.ui().onValueUpdate(vu -> {
			if(vu.key().equals(Constants.ACCENT_COLOR_KEY))
				save();
			
			maybeQueue(() -> app.getWindows().forEach(wnd -> {
				if(wnd instanceof JajaFXAppWindow paw) {
					paw.reloadCss();
				} 
			}));
		});
	}
	
	public Path managedStyles() {
		return managedFile;
	}
	
	private void save() {
		try(var out = new PrintWriter(Files.newBufferedWriter(managedFile), true)) {
			out.println("/* This file is managed by Pretty, do not edit */");
			out.println(".root {");
			out.format("  accent_color: %s;%n", configuration.ui().get(Constants.ACCENT_COLOR_KEY));
			out.println("}");
		}
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}
	
}
