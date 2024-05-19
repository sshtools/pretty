pipeline {
 	agent none
 	tools {
		maven 'Maven 3.9.0' 
		jdk 'Graal JDK 22' 
	}

	stages {
		stage ('Pretty Installers') {
			parallel {
				/*
				 * Linux Installers and Packages
				 */
				stage ('Linux Pretty Installers') {
					agent {
						label 'install4j && linux'
					}
					steps {
						configFileProvider([
					 			configFile(
					 				fileId: 'bb62be43-6246-4ab5-9d7a-e1f35e056d69',  
					 				replaceTokens: true,
					 				targetLocation: 'hypersocket.build.properties',
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
					 			globalMavenSettingsConfig: '4bc608a8-6e52-4765-bd72-4763f45bfbde'
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
					 				fileId: 'bb62be43-6246-4ab5-9d7a-e1f35e056d69',  
					 				replaceTokens: true,
					 				targetLocation: 'hypersocket.build.properties',
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
					 			globalMavenSettingsConfig: '4bc608a8-6e52-4765-bd72-4763f45bfbde'
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
					 				fileId: 'bb62be43-6246-4ab5-9d7a-e1f35e056d69',  
					 				replaceTokens: true,
					 				targetLocation: 'hypersocket.build.properties',
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
					 			globalMavenSettingsConfig: '4bc608a8-6e52-4765-bd72-4763f45bfbde'
					 		) {
					 			// -Dinstall4j.disableNotarization=true 
					 		  	sh 'mvn -U -Dbuild.mediaTypes=macos,macosFolder,macosFolderArchive ' +
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
    			withMaven(
		 			globalMavenSettingsConfig: '4bc608a8-6e52-4765-bd72-4763f45bfbde',
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