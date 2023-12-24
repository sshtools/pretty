package com.sshtools.pretty;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Manifest;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

public class AppVersion {

	static Map<String,String> versions = Collections.synchronizedMap(new HashMap<>());
	
	public static String getVersion() {
		return getVersion("pretty");
	}
	
	public static String getVersion(String artifactId) {
		String fakeVersion = Boolean.getBoolean("jadaptive.development") ? 
				System.getProperty("jadaptive.development.version", System.getProperty("jadaptive.devVersion")) : null;
		if(fakeVersion != null) {
			return fakeVersion;
		}
		
	    String detectedVersion = versions.get(artifactId);
	    if(detectedVersion != null)
	    	return detectedVersion;

	    /* Load the MANIFEST.MF from all jars looking for the X-Extension-Version
	     * attribute. Any jar that has the attribute also can optionally have
	     * an X-Extension-Priority attribute. The highest priority is the
	     * version that will be used.
	     */
	    ClassLoader cl = Thread.currentThread().getContextClassLoader();
	    if(cl == null)
	    	cl = AppVersion.class.getClassLoader();
		try {
			int highestPriority = -1;
			String highestPriorityVersion = null;
		    for(Enumeration<URL> en = cl.getResources("META-INF/MANIFEST.MF");
		    		en.hasMoreElements(); ) {
		    	URL url = en.nextElement();
		    	try(InputStream in = url.openStream()) {
			    	Manifest mf = new Manifest(in);	
			    	String extensionVersion = mf.getMainAttributes().getValue("X-Extension-Version");
			    	if(extensionVersion != null && extensionVersion.length() > 0) {
				    	String priorityStr = mf.getMainAttributes().getValue("X-Extension-Priority");
				    	int priority = priorityStr == null || priorityStr.length() == 0 ? 0 : Integer.parseInt(priorityStr);
				    	if(priority > highestPriority) {
				    		highestPriorityVersion = extensionVersion;
				    	}
			    	}
		    	}
		    }
		    if(highestPriorityVersion != null)
		    	detectedVersion = highestPriorityVersion;
	    }
	    catch(Exception e) {

	    }

	    // try to load from maven properties first
		if(detectedVersion == null || detectedVersion.length() == 0) {
		    try {
		        Properties p = new Properties();
		        InputStream is = cl.getResourceAsStream("META-INF/maven/com.sshtools/" + artifactId + "/pom.properties");
		        if(is == null) {
			        is = AppVersion.class.getResourceAsStream("/META-INF/maven/com.sshtools/" + artifactId + "/pom.properties");
		        }
		        if (is != null) { 
		            p.load(is);
		            detectedVersion = p.getProperty("version", "");
		        }
		    } catch (Exception e) {
		        // ignore
		    }
		}

	    // fallback to using Java API
		if(detectedVersion == null || detectedVersion.length() == 0) {
	        Package aPackage = AppVersion.class.getPackage();
	        if (aPackage != null) {
	            detectedVersion = aPackage.getImplementationVersion();
	            if (detectedVersion == null) {
	                detectedVersion = aPackage.getSpecificationVersion();
	            }
	        }
	    }

		if(detectedVersion == null || detectedVersion.length() == 0) {
	    	try {
	    		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
	            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
	            Document doc = docBuilder.parse (new File("pom.xml"));
	            detectedVersion = doc.getDocumentElement().getElementsByTagName("version").item(0).getTextContent();
	    	} catch (Exception e) {
			} 
	        
	    }

		if(detectedVersion == null || detectedVersion.length() == 0) {
			detectedVersion = "DEV_VERSION";
		}

	    /* Treat snapshot versions as build zero */
	    if(detectedVersion.endsWith("-SNAPSHOT")) {
	    	detectedVersion = detectedVersion.substring(0, detectedVersion.length() - 9) + "-0";
	    }

	    versions.put(artifactId, detectedVersion);

	    return detectedVersion;
	}

	public static String getProductId() {
		return System.getProperty("jadaptive.id", "pretty");
	} 
	
	public static String getBrandId() {
		String id = getProductId();
		int idx = id.indexOf('-');
		if(idx==-1) {
			throw new IllegalStateException("Product id must consist of string formatted like <brand>-<product>");
		}
		return id.substring(0, idx);
	} 
}
