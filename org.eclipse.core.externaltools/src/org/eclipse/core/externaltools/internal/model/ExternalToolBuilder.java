/*******************************************************************************
 * Copyright (c) 2000, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matthew Conway  - Bug 175186
 *******************************************************************************/
package org.eclipse.core.externaltools.internal.model;


import java.util.Map;

import org.eclipse.core.externaltools.internal.ExternalToolsCore;
import org.eclipse.core.externaltools.internal.IExternalToolConstants;
import org.eclipse.core.externaltools.internal.launchConfigurations.ExternalToolsCoreUtil;
import org.eclipse.core.externaltools.internal.registry.ExternalToolMigration;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.Bundle;

/**
 * This project builder implementation will run an external tool during the
 * build process. 
 */
public final class ExternalToolBuilder extends IncrementalProjectBuilder {
	private final class IgnoreTeamPrivateChanges implements IResourceDeltaVisitor {
		private boolean[] fTrueChange;
		private IgnoreTeamPrivateChanges(boolean[] trueChange) {
			super();
			fTrueChange= trueChange;
		}
		public boolean visit(IResourceDelta visitDelta) throws CoreException {
			IResource resource= visitDelta.getResource();
			if (resource instanceof IFile) {
				fTrueChange[0]= true;
				return false;
			}
			return true;
		}
	}

	public static final String ID = "org.eclipse.ui.externaltools.ExternalToolBuilder"; //$NON-NLS-1$;

	private static String buildType = IExternalToolConstants.BUILD_TYPE_NONE;
	
	private static IProject buildProject= null;
    private static IResourceDelta buildDelta= null;
	
	/* (non-Javadoc)
	 * @see org.eclipse.core.internal.events.InternalBuilder#build(int, java.util.Map, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected IProject[] build(int kind, Map args, IProgressMonitor monitor) throws CoreException {		
		if (ExternalToolsCore.getDefault().getBundle().getState() != Bundle.ACTIVE) {
			return null;
		}
		
		ILaunchConfiguration config= BuilderCoreUtils.configFromBuildCommandArgs(getProject(), args, new String[1]);
        if (config == null) {
            throw ExternalToolsCore.newError(ExternalToolsModelMessages.ExternalToolBuilder_0, null);
        }
		IProject[] projectsWithinScope= null;
		IResource[] resources = ExternalToolsCoreUtil.getResourcesForBuildScope(config);
		if (resources != null) {
			projectsWithinScope= new IProject[resources.length];
			for (int i = 0; i < resources.length; i++) {
				projectsWithinScope[i]= resources[i].getProject();
			}
		}
        boolean kindCompatible= commandConfiguredForKind(config, kind);
        if (kindCompatible && configEnabled(config)) {
            doBuildBasedOnScope(resources, kind, config, monitor);
        }
        
		return projectsWithinScope;
	}

    private boolean commandConfiguredForKind(ILaunchConfiguration config, int kind) {
        try {
            if (!(config.getAttribute(IExternalToolConstants.ATTR_TRIGGERS_CONFIGURED, false))) {
                ICommand command= getCommand();
                //adapt the builder command to make use of the 3.1 support for setting command build kinds
                //this will only happen once for builder/command defined before the support existed
                BuilderCoreUtils.configureTriggers(config, command);
                IProjectDescription desc= getProject().getDescription();
                ICommand[] commands= desc.getBuildSpec();
                int index= getBuilderCommandIndex(commands, command);
                if (index != -1) {
                    commands[index]= command;
                    desc.setBuildSpec(commands);
                    getProject().setDescription(desc, null);
                    ILaunchConfigurationWorkingCopy copy= config.getWorkingCopy();
                    copy.setAttribute(IExternalToolConstants.ATTR_TRIGGERS_CONFIGURED, true);
                    copy.doSave();
                }
                return command.isBuilding(kind);
            }
        } catch (CoreException e) {
           ExternalToolsCore.log(e);
           return true;
        }
        return true;
    }
    
    private int getBuilderCommandIndex(ICommand[] buildSpec, ICommand command) {
        Map commandArgs= command.getArguments();
        if (commandArgs == null) {
            return -1;
        }
        String handle= (String) commandArgs.get(BuilderCoreUtils.LAUNCH_CONFIG_HANDLE);
        if (handle == null) {
            return -1;
        }
        for (int i = 0; i < buildSpec.length; ++i) {
            ICommand buildSpecCommand= buildSpec[i];
            if (ID.equals(buildSpecCommand.getBuilderName())) {
                Map buildSpecArgs= buildSpecCommand.getArguments();
                if (buildSpecArgs != null) {
                    String buildSpecHandle= (String) buildSpecArgs.get(BuilderCoreUtils.LAUNCH_CONFIG_HANDLE);
                    if (handle.equals(buildSpecHandle)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

	/**
	 * Returns whether the given builder config is enabled or not.
	 * 
	 * @param config the config to examine
	 * @return whether the config is enabled
	 */
	private boolean configEnabled(ILaunchConfiguration config) {
		try {
			return ExternalToolsCoreUtil.isBuilderEnabled(config);
		} catch (CoreException e) {
			ExternalToolsCore.log(e);
		}
		return true;
	}

	private void doBuildBasedOnScope(IResource[] resources, int kind, ILaunchConfiguration config, IProgressMonitor monitor) throws CoreException {
		boolean buildForChange = true;
		if (kind != FULL_BUILD) { //scope not applied for full builds
			if (resources != null && resources.length > 0) {
				buildForChange = buildScopeIndicatesBuild(resources);
			}
		}

		if (buildForChange) {
			launchBuild(kind, config, monitor);
		}
	}
	
	private void launchBuild(int kind, ILaunchConfiguration config, IProgressMonitor monitor) throws CoreException {
		monitor.subTask(NLS.bind(ExternalToolsModelMessages.ExternalToolBuilder_Running__0_____1, new String[] { config.getName()}));
		buildStarted(kind);
		// The default value for "launch in background" is true in debug core. If
		// the user doesn't go through the UI, the new attribute won't be set. This means
		// that existing Ant builders will try to run in the background (and likely conflict with
		// each other) without migration.
		config= ExternalToolMigration.migrateRunInBackground(config);
		config.launch(ILaunchManager.RUN_MODE, monitor);
		buildEnded();
	}

	/**
	 * Returns the build type being performed if the
	 * external tool is being run as a project builder.
	 * 
	 * @return one of the <code>IExternalToolConstants.BUILD_TYPE_*</code> constants.
	 */
	public static String getBuildType() {
		return buildType;
	}
	
	/**
	 * Returns the project that is being built and has triggered the current external
	 * tool builder. <code>null</code> is returned if no build is currently occurring.
	 * 
	 * @return project being built or <code>null</code>.
	 */
	public static IProject getBuildProject() {
		return buildProject;
	}

    /**
     * Returns the <code>IResourceDelta</code> that is being built and has triggered the current external
     * tool builder. <code>null</code> is returned if no build is currently occurring.
     * 
     * @return resource delta for the build or <code>null</code>
     */
    public static IResourceDelta getBuildDelta() {
        return buildDelta;
    }
    
	/**
	 * Stores the currently active build kind and build project when a build begins
	 * @param buildKind
	 */
	private void buildStarted(int buildKind) {
		switch (buildKind) {
			case IncrementalProjectBuilder.INCREMENTAL_BUILD :
				buildType = IExternalToolConstants.BUILD_TYPE_INCREMENTAL;
				buildDelta = getDelta(getProject());
				break;
			case IncrementalProjectBuilder.FULL_BUILD :
				buildType = IExternalToolConstants.BUILD_TYPE_FULL;
				break;
			case IncrementalProjectBuilder.AUTO_BUILD :
				buildType = IExternalToolConstants.BUILD_TYPE_AUTO;
				buildDelta = getDelta(getProject());
				break;
            case IncrementalProjectBuilder.CLEAN_BUILD :
                buildType = IExternalToolConstants.BUILD_TYPE_CLEAN;
                break;
			default :
				buildType = IExternalToolConstants.BUILD_TYPE_NONE;
				break;
		}
		buildProject= getProject();
	}
	
	/**
	 * Clears the current build kind, build project and build delta when a build finishes.
	 */
	private void buildEnded() {
		buildType= IExternalToolConstants.BUILD_TYPE_NONE;
		buildProject= null;
        buildDelta= null;
	}
	
	private boolean buildScopeIndicatesBuild(IResource[] resources) {
		for (int i = 0; i < resources.length; i++) {
			IResourceDelta delta = getDelta(resources[i].getProject());
			if (delta == null) {
				//project just added to the workspace..no previous build tree
				return true;
			} 
			IPath path= resources[i].getProjectRelativePath();
			IResourceDelta change= delta.findMember(path);
			if (change != null) {
				final boolean[] trueChange= new boolean[1];
				trueChange[0]= false;
				try {
					change.accept(new IgnoreTeamPrivateChanges(trueChange));
				} catch (CoreException e) {
					ExternalToolsCore.log("Internal error resolving changed resources during build", e); //$NON-NLS-1$
				}
				
				return trueChange[0]; //filtered out team private changes
			}
		}
		return false;
	}
    
    protected void clean(IProgressMonitor monitor) throws CoreException {
	    ICommand command= getCommand();
        ILaunchConfiguration config= BuilderCoreUtils.configFromBuildCommandArgs(getProject(), command.getArguments(), new String[1]);
    	if (!configEnabled(config)) {
	    	return;
	    }
        
        if ((!config.getAttribute(IExternalToolConstants.ATTR_TRIGGERS_CONFIGURED, false))) {
            //old behavior
            super.clean(monitor);
            return;
        }
	
		launchBuild(IncrementalProjectBuilder.CLEAN_BUILD, config, monitor);
    }
}