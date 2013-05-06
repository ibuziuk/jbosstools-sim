/*******************************************************************************
 * Copyright (c) 2007-2013 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributor:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.vpe.cordovasim.eclipse.launch.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.jboss.tools.vpe.browsersim.eclipse.launcher.BrowserSimLauncher;
import org.jboss.tools.vpe.browsersim.eclipse.launcher.ExternalProcessCallback;
import org.jboss.tools.vpe.browsersim.eclipse.launcher.ExternalProcessLauncher;

/**
 * @author "Yahor Radtsevich (yradtsevich)"
 */
public class CordovaSimLauncher {
	public static final String CORDOVASIM_CLASS_NAME = "org.jboss.tools.vpe.cordovasim.CordovaSimRunner"; //$NON-NLS-1$
	private static final List<ExternalProcessCallback> CORDOVASIM_CALLBACKS = BrowserSimLauncher.BROWSERSIM_CALLBACKS;
	private static final List<String> REQUIRED_BUNDLES = new ArrayList<String>(); 
	static {
		REQUIRED_BUNDLES.addAll(BrowserSimLauncher.REQUIRED_BUNDLES);
		REQUIRED_BUNDLES.addAll(Arrays.asList(
			"org.jboss.tools.vpe.cordovasim",
			"org.jboss.tools.vpe.cordovasim.ripple",
			"org.eclipse.jetty.continuation",
			"org.eclipse.jetty.continuation",
			"org.eclipse.jetty.http",
			"org.eclipse.jetty.io",
		"org.eclipse.jetty.security",
			"org.eclipse.jetty.server",
			"org.eclipse.jetty.servlet",
			"org.eclipse.jetty.util",
		"org.eclipse.jetty.client",
		"org.eclipse.jetty.servlets",
		"org.eclipse.jetty.rewrite",
			"javax.servlet"
		));
	}
	
	private static final List<String> OPTIONAL_BUNDLES = BrowserSimLauncher.OPTIONAL_BUNDLES;	

	//if you change this parameter, see also @org.jbosstools.browsersim.ui.BrowserSim
	private static final String NOT_STANDALONE = BrowserSimLauncher.NOT_STANDALONE;	

	public static void launchCordovaSim(String projectString, String rootFolderString, String startPageString,
			Integer port) {
		List<String> parameters = new ArrayList<String>();
		parameters.add(NOT_STANDALONE);

		IContainer rootFolder = null;
		IProject project = null;
		if (projectString != null) {
			project = CordovaSimLaunchParametersUtil.getProject(projectString);
			
			if (rootFolderString != null) {
				rootFolder = CordovaSimLaunchParametersUtil.getRootFolder(project, rootFolderString);
			} else {
				rootFolder = CordovaSimLaunchParametersUtil.getDefaultRootFolder(project);
			}
			parameters.add(rootFolder.getLocation().toString());
		}
		
		String actualStartPageString;
		if (startPageString != null) {
			actualStartPageString = startPageString;
		} else {
			IResource startPage = CordovaSimLaunchParametersUtil.getDefaultStartPage(project, rootFolder);
			IPath startPagePath = CordovaSimLaunchParametersUtil.getRelativePath(rootFolder, startPage);
			actualStartPageString = startPagePath.toString();
		}
		parameters.add(actualStartPageString);
		
		if (port != null) {
			parameters.add("-port");
			parameters.add(String.valueOf(port));
		}

		ExternalProcessLauncher.launchAsExternalProcess(REQUIRED_BUNDLES, OPTIONAL_BUNDLES,
				CORDOVASIM_CALLBACKS, CORDOVASIM_CLASS_NAME, parameters);
	}
}
