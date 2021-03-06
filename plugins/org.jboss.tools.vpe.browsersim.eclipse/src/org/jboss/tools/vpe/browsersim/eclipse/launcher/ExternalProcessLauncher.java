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
package org.jboss.tools.vpe.browsersim.eclipse.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchListener;
import org.eclipse.ui.PlatformUI;
import org.jboss.tools.vpe.browsersim.eclipse.Activator;
import org.jboss.tools.vpe.browsersim.eclipse.Messages;
import org.jboss.tools.vpe.browsersim.eclipse.dialog.BrowserSimErrorDialog;
import org.jboss.tools.vpe.browsersim.eclipse.launcher.internal.ExternalProcessPostShutdownDestroyer;
import org.jboss.tools.vpe.browsersim.eclipse.preferences.PreferencesUtil;
import org.osgi.framework.Bundle;

/**
 * @author Yahor Radtsevich (yradtsevich)
 */
public class ExternalProcessLauncher {
	private static String PATH_SEPARATOR = System.getProperty("path.separator"); //$NON-NLS-1$
	
	public static void launchAsExternalProcess(List<String> bundles, List<String> resourcesBundles,
			final List<ExternalProcessCallback> callbacks, String className, List<String> parameters, final String programName, IVMInstall jvm) {
		try {			
			String classPath = getClassPathString(bundles, resourcesBundles);
			
			String javaCommand = PreferencesUtil.getJavaCommand(jvm);
			if (javaCommand != null) {
				List<String> commandElements = new ArrayList<String>();
				commandElements.add(javaCommand);
				
				if (Platform.OS_MACOSX.equals(Platform.getOS())) {
					commandElements.add("-XstartOnFirstThread"); //$NON-NLS-1$
					if (Platform.ARCH_X86.equals(Platform.getOSArch())) {
						commandElements.add("-d32"); //$NON-NLS-1$
					}
				}
				
				commandElements.add("-cp"); //$NON-NLS-1$
				commandElements.add(classPath);
				commandElements.add(className);
				
				//optional parameters
				for (String parameter : parameters) {
					commandElements.add(parameter);
				}
				
				ProcessBuilder processBuilder = new ProcessBuilder(commandElements);
				processBuilder.directory(ConfigurationScope.INSTANCE.getLocation().toFile());
				
				Process browserSimProcess = processBuilder.start();
				final IWorkbenchListener browserSimPostShutDownDestroyer = new ExternalProcessPostShutdownDestroyer(browserSimProcess);
				PlatformUI.getWorkbench().addWorkbenchListener(browserSimPostShutDownDestroyer);
				
				final InputStreamReader errorReader = new InputStreamReader(browserSimProcess.getErrorStream());
				final Reader inputReader = new InputStreamReader(browserSimProcess.getInputStream());
				new Thread() {
					public void run() {
						try {
							TransparentReader transparentReader = new TransparentReader(inputReader, System.out);
							String nextLine;
							while ((nextLine = transparentReader.readLine(true)) != null) {
								for (ExternalProcessCallback callback : callbacks) { 
									if (nextLine.startsWith(callback.getCallbackId())) {
										callback.call(nextLine, transparentReader);
									}
								}
							}
						} catch (IOException e) {
							Activator.logError(e.getMessage(), e);
						}  finally {
							PlatformUI.getWorkbench().removeWorkbenchListener(browserSimPostShutDownDestroyer);
						}
					};
				}.start();
				new Thread() {
					public void run() {
						int nextCharInt;
						try {
							while ((nextCharInt = errorReader.read()) >= 0) {
								System.err.print((char) nextCharInt);
							}
						} catch (IOException e) {
							Activator.logError(e.getMessage(), e);
						}
					};
				}.start();
			} else {
				showErrorDialog(programName);
			}
		} catch (IOException e) {
			Activator.logError(e.getMessage(), e);
		}		
	}
	
	public static void showErrorDialog(final String programName) {
		Display.getDefault().asyncExec(new Runnable() {
			
			@Override
			public void run() {
				Shell shell = Display.getDefault().getActiveShell();
				if (shell == null) {
					shell = Display.getDefault().getShells()[0]; // Hot fix for gtk3 
				}
				BrowserSimErrorDialog e = new BrowserSimErrorDialog(shell, Messages.ExternalProcessLauncher_ERROR, shell.getDisplay().getSystemImage(SWT.ICON_ERROR),
						programName, MessageDialog.ERROR, new String[] {IDialogConstants.OK_LABEL}, 0); 
				e.open();
			}
		});
	}
	
	private static String getClassPathString(List<String> bundles, List<String> resourcesBundles) throws IOException {
		List<Bundle> classPathBundles = new ArrayList<Bundle>();
		for (String bundleName : bundles) {
			Bundle bundle = Platform.getBundle(bundleName);
			if (bundle != null) {
				classPathBundles.add(bundle);
			}
		}
					
		StringBuilder classPath = new StringBuilder();
		if (classPathBundles.size() > 0) {
			for (int i = 0; i < classPathBundles.size() - 1; i++) {
				classPath.append(getBundleLocation(classPathBundles.get(i)));
				classPath.append(PATH_SEPARATOR);
			}
			classPath.append(getBundleLocation(classPathBundles.get(classPathBundles.size() - 1)));
		}	
		
		for (String bundleName : resourcesBundles) {
			Bundle bundle = Platform.getBundle(bundleName);
			if (bundle != null) {
				classPath.append(getResource(bundle));
			}
		}
		
		return classPath.toString();
	}
	
	public static String getBundleLocation(Bundle bundle) throws IOException {
		try {
			File bundleLocation = FileLocator.getBundleFile(bundle);
			
			if (bundleLocation.isDirectory()) {
				File binDirectory = new File(bundleLocation, "bin"); //$NON-NLS-1$
				if (binDirectory.isDirectory()) {
					bundleLocation = binDirectory;
				}
			}
	
			return bundleLocation.getCanonicalPath();
		} catch (IOException e) {
			throw new IOException(Messages.ExternalProcessLauncher_NO_BUNDLE + bundle.getSymbolicName(), e);
		}
	}
	
	private static String getResource(Bundle bundle) throws IOException {
		StringBuilder result = new StringBuilder();
		
		//URL[] res = FileLocator.findEntries(bundle, new Path("plugins"));
		String location = FileLocator.getBundleFile(bundle).getCanonicalPath(); 
		File resources = new File(location + "/plugins"); //$NON-NLS-1$
		if (resources.exists()) {
			for(File resource : resources.listFiles()) {
				result.append(PATH_SEPARATOR);
				result.append(resource.getCanonicalPath());
			}
		}
		return result.toString();
	}
}
