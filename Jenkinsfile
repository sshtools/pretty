pipeline {
 	agent none
 	tools {
		maven 'Maven 3.9.0' 
		jdk 'Graal JDK 22' 
	}
	
	environment {
	    /* Constants / Configuration */
	    BUILD_PROPERTIES_ID = "b60f3998-d8fd-434b-b3c8-ed52aa52bc2e"
	    BUILD_PROPERTIES_NAME = "jadaptive.build.properties"
	    MAVEN_CONFIG_ID = "14324b85-c597-44e8-a575-61f925dba528"
	}

	stages {
		stage ('Pretty Installers') {
			parallel {
				/*
				 * Linux Installers and Packages
				 */
				stage ('Linux Pretty Installers') {
					agent {
						label 'install4j && linux && x86_64'
					}
					steps {
						configFileProvider([
					 			configFile(
					 				fileId: "${env.BUILD_PROPERTIES_ID}",  
					 				replaceTokens: true,
					 				targetLocation: "${env.BUILD_PROPERTIES_NAME}",
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
					 			globalMavenSettingsConfig: "${env.MAVEN_CONFIG_ID}"
					 		) {
					 		  	sh 'mvn -U -Dbuild.mediaTypes=unixInstaller,unixArchive,linuxRPM,linuxDeb ' +
					 		  	   '-Dbuild.projectProperties=$BUILD_PROPERTIES ' +
					 		  	   '-DbuildInstaller=true -P translate clean package'
					 		  	
					 		  	/* Stash installers */
			        			stash includes: 'target/media/*', name: 'linux-pretty'
			        			
			        			/* Stash updates.xml */
			        			dir('target/media') {
									stash includes: 'updates.xml', name: 'linux-pretty-updates-xml'
			        			}
					 		}
        				}
					}
				}
				
				/*
				 * Windows installers
				 */
				stage ('Windows Pretty Installers') {
					agent {
						label 'install4j && windows'
					}
					steps {
						configFileProvider([
					 			configFile(
					 				fileId: "${env.BUILD_PROPERTIES_ID}",  
					 				replaceTokens: true,
					 				targetLocation: "${env.BUILD_PROPERTIES_NAME}",
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
					 			globalMavenSettingsConfig: "${env.MAVEN_CONFIG_ID}"
					 		) {
					 		  	bat 'mvn -U -Dinstall4j.verbose=true -Dbuild.mediaTypes=windows,windowsArchive ' +
					 		  	    '"-Dbuild.projectProperties=%BUILD_PROPERTIES%" ' +
				 		  	        '-DbuildInstaller=true -P translate clean package'
					 		  	
					 		  	/* Stash installers */
			        			stash includes: 'target/media/*', name: 'windows-pretty'
			        			
			        			/* Stash updates.xml */
			        			dir('target/media') {
									stash includes: 'updates.xml', name: 'windows-pretty-updates-xml'
			        			}
					 		}
        				}
					}
				}
				
				/*
				 * MacOS installers
				 */
				stage ('MacOS Pretty Installers') {
					agent {
						label 'install4j && macos'
					}
					steps {
						configFileProvider([
					 			configFile(
					 				fileId: "${env.BUILD_PROPERTIES_ID}",  
					 				replaceTokens: true,
					 				targetLocation: "${env.BUILD_PROPERTIES_NAME}",
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
					 			globalMavenSettingsConfig: "${env.MAVEN_CONFIG_ID}"
					 		) {
					 			// -Dinstall4j.disableNotarization=true 
					 		  	sh 'mvn -X -U -Dbuild.mediaTypes=macos,macosFolder,macosFolderArchive ' +
					 		  	   '-Dbuild.projectProperties=$BUILD_PROPERTIES ' +
					 		  	   '-DbuildInstaller=true -P translate clean package'
					 		  	
					 		  	/* Stash installers */
			        			stash includes: 'target/media/*', name: 'macos-pretty'
			        			
			        			/* Stash updates.xml */
			        			dir('target/media') {
									stash includes: 'updates.xml', name: 'macos-pretty-updates-xml'
			        			}
					 		}
        				}
					}
				}
			}
		}
		
		stage ('Deploy') {
			agent {
				label 'linux'
			}
			steps {
			
				script {
					/* Create full version number from Maven POM version and the
					   build number */
					def pom = readMavenPom file: 'pom.xml'
					pom_version_array = pom.version.split('\\.')
					suffix_array = pom_version_array[2].split('-')
					env.FULL_VERSION = pom_version_array[0] + '.' + pom_version_array[1] + "." + suffix_array[0] + "-${BUILD_NUMBER}"
					echo 'Full Maven Version ' + env.FULL_VERSION
				}
				
				/* Unstash installers */
	 		  	unstash 'linux-pretty'
	 		  	unstash 'windows-pretty'
	 		  	unstash 'macos-pretty'
	 		  	
				/* Unstash updates.xml */
	 		  	dir('target/media-linux') {
	 		  		unstash 'linux-pretty-updates-xml'
    			}
	 		  	dir('target/media-windows') {
	 		  		unstash 'windows-pretty-updates-xml'
    			}
	 		  	dir('target/media-macos') {
	 		  		unstash 'macos-pretty-updates-xml'
    			}
    			
    			/* Merge all updates.xml into one */
    			withMaven(globalMavenSettingsConfig: "${env.MAVEN_CONFIG_ID}"
		 		) {
					sh 'mvn -P merge-installers com.sshtools:updatesxmlmerger-maven-plugin:merge'
		 		}
		 		
    			/* Upload all CLI and GUI installers */
		 		s3Upload(
		 			consoleLogLevel: 'INFO', 
		 			dontSetBuildResultOnFailure: false, 
		 			dontWaitForConcurrentBuildCompletion: false, 
		 			entries: [[
		 				bucket: 'sshtools-public/pretty/' + env.FULL_VERSION, 
		 				noUploadOnFailure: true, 
		 				selectedRegion: 'eu-west-1', 
		 				sourceFile: 'target/media/*', 
		 				storageClass: 'STANDARD', 
		 				useServerSideEncryption: false]], 
		 			pluginFailureResultConstraint: 'FAILURE', 
		 			profileName: 'JADAPTIVE Buckets', 
		 			userMetadata: []
		 		)
		 		
    			/* Copy the merged updates.xml (for FileDrop) to the nightly directory so updates can be seen
    			by anyone on this channel */
		 		s3Upload(
		 			consoleLogLevel: 'INFO', 
		 			dontSetBuildResultOnFailure: false, 
		 			dontWaitForConcurrentBuildCompletion: false, 
		 			entries: [[
		 				bucket: 'sshtools-public/pretty/continuous', 
		 				noUploadOnFailure: true, 
		 				selectedRegion: 'eu-west-1', 
		 				sourceFile: 'target/media/updates.xml', 
		 				storageClass: 'STANDARD', 
		 				useServerSideEncryption: false]], 
		 			pluginFailureResultConstraint: 'FAILURE', 
		 			profileName: 'JADAPTIVE Buckets', 
		 			userMetadata: []
		 		)
			}					
		}		
	}
}