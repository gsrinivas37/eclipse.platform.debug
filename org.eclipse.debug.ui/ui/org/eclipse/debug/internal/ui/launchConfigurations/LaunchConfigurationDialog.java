package org.eclipse.debug.internal.ui.launchConfigurations;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationListener;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.IDebugHelpContextIds;
import org.eclipse.debug.internal.ui.IDebugPreferenceConstants;
import org.eclipse.debug.internal.ui.SWTUtil;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.ILaunchConfigurationDialog;
import org.eclipse.debug.ui.ILaunchConfigurationTab;
import org.eclipse.debug.ui.ILaunchConfigurationTabGroup;
import org.eclipse.jface.dialogs.ControlEnableState;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.operation.ModalContext;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.wizard.ProgressMonitorPart;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.model.WorkbenchViewerSorter;

/**
 * The dialog used to edit and launch launch configurations.
 */
public class LaunchConfigurationDialog extends TitleAreaDialog 
										implements ISelectionChangedListener, 
													ILaunchConfigurationListener, 
													ILaunchConfigurationDialog, 
													IDoubleClickListener {

	/**
	 * The tree of launch configurations
	 */
	private TreeViewer fConfigTree;
	
	private Object fContext;
	
	private ILaunchConfiguration fFirstConfig;
	
	/**
	 * Whether to perform single-click launching when
	 * opened (if the pref is on).
	 */
	private boolean fTrySingleClick = true;
	
	/**
	 * The starting mode, as specified by the caller
	 */
	private String fMode;
	
	/**
	 * The launch configuration edit area.
	 */
	private Control fEditArea;
	
	/**
	 * The 'new' button to create a new configuration
	 */
	private Button fNewButton;
	
	/**
	 * The 'delete' button to delete selected configurations
	 */
	private Button fDeleteButton;
	
	/**
	 * The 'copy' button to create a copy of the selected config
	 */
	private Button fCopyButton;
	
	/**
	 * The 'save' button
	 */
	private Button fSaveButton;	
	
	/**
	 * The text widget displaying the name of the
	 * launch configuration under edit
	 */
	private Text fNameText;
	
	private String fLastSavedName = null;
	
	/**
	 * The tab folder
	 */
	private TabFolder fTabFolder;
	
	/**
	 * The current (working copy) launch configuration
	 * being displayed/edited or <code>null</code> if
	 * none
	 */
	private ILaunchConfigurationWorkingCopy fWorkingCopy;
	
	/**
	 * The actual (non-working copy) launch configuration that underlies the current working copy
	 */
	private ILaunchConfiguration fUnderlyingConfig;
	
	/**
	 * Clients of this dialog may set an 'initial configuration type', which means that when
	 * the dialog is opened, a configuration of that type will be created, initialized, and
	 * saved.  Note that the initial config type is ignored if single-click launching is enabled.
	 */
	private ILaunchConfigurationType fInitialConfigType;
	
	/**
	 * The current tab group being displayed
	 */
	private ILaunchConfigurationTabGroup fTabGroup;
	
	/**
	 * The type of config tabs are currently displayed
	 * for
	 */
	private ILaunchConfigurationType fTabType;
	
	/** 
	 * The index of the currently selected tab
	 */
	private int fCurrentTabIndex;
	
	private ProgressMonitorPart fProgressMonitorPart;
	private Cursor waitCursor;
	private Cursor arrowCursor;
	private MessageDialog fWindowClosingDialog;
	
	private SelectionAdapter fCancelListener = new SelectionAdapter() {
		public void widgetSelected(SelectionEvent evt) {
			cancelPressed();
		}
	};
	/**
	 * Whether initlialing tabs
	 */
	private boolean fInitializingTabs = false;
		
	/**
	 * Indicates if selection changes in the tree should be ignored
	 */
	private boolean fIgnoreSelectionChanges = false;
	
	/**
	 * Previously selected element in the tree
	 */
	private Object fSelectedTreeObject;
	
	/**
	 * The number of 'long-running' operations currently taking place in this dialog
	 */	
	private long fActiveRunningOperations = 0;
		
	/**
	 * Id for 'Launch' button.
	 */
	protected static final int ID_LAUNCH_BUTTON = IDialogConstants.CLIENT_ID + 1;
	
	/**
	 * Constrant String used as key for setting and retrieving current Control with focus
	 */
	private static final String FOCUS_CONTROL = "focusControl";//$NON-NLS-1$

	/**
	 * The height in pixels of this dialog's progress indicator
	 */
	private static int PROGRESS_INDICATOR_HEIGHT = 18;

	/**
	 * Empty array
	 */
	protected static final Object[] EMPTY_ARRAY = new Object[0];	
	
	protected static final String DEFAULT_NEW_CONFIG_NAME = "New_configuration";
	
	/**
	 * Status area messages
	 */
	protected static final String LAUNCH_STATUS_OK_MESSAGE = "Ready to launch";
	protected static final String LAUNCH_STATUS_STARTING_FROM_SCRATCH_MESSAGE 
										= "Select a configuration to launch or a config type to create a new configuration";
	
	/**
	 * Constructs a new launch configuration dialog on the given
	 * parent shell.
	 * 
	 * @param shell the parent shell
	 * @param selection the selection used to initialize this dialog, typically the 
	 *  current workbench selection
	 * @param mode one of <code>ILaunchManager.RUN_MODE</code> or 
	 *  <code>ILaunchManager.DEBUG_MODE</code>
	 */
	public LaunchConfigurationDialog(Shell shell, IStructuredSelection selection, String mode) {
		super(shell);
		setContext(resolveContext(selection));
		setMode(mode);
	}
	
	/**
	 * Returns the Object to be used as context for this dialog, derived from the specified selection.
	 * If the specified selection has as its first element an IFile whose extension matches
	 * <code>ILaunchConfiguration.LAUNCH_CONFIGURATION_FILE_EXTENSION</code>, then return
	 * the launch configuration declared in the IFile.  Otherwise, return the first element 
	 * in the specified selection.
	 */
	protected Object resolveContext(IStructuredSelection selection) {
		
		// Empty selection means no context
		if ((selection == null) || (selection.isEmpty())) {
			return null;
		} 

		// If first element is a launch config file, create a launch configuration from it
		// and make this the context, otherwise just return the first element
		Object firstSelected = selection.getFirstElement();
		if (firstSelected instanceof IFile) {
			IFile file = (IFile) firstSelected;
			if (file.getFileExtension().equals(ILaunchConfiguration.LAUNCH_CONFIGURATION_FILE_EXTENSION)) {
				return getLaunchManager().getLaunchConfiguration(file);
			}
		}
		return firstSelected;
	}
	
	/**
	 * A launch configuration dialog overrides this method
	 * to create a custom set of buttons in the button bar.
	 * This dialog has 'Launch' and 'Cancel'
	 * buttons.
	 * 
	 * @see org.eclipse.jface.dialogs.Dialog#createButtonsForButtonBar(Composite)
	 */
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, ID_LAUNCH_BUTTON, getLaunchButtonText(), true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}
	
	/**
	 * Returns the appropriate text for the launch button - run or debug.
	 */
	protected String getLaunchButtonText() {
		if (getMode() == ILaunchManager.DEBUG_MODE) {
			return "Deb&ug";
		} else {
			return "R&un";
		}
	}

	/**
	 * Handle the 'save and launch' & 'launch' buttons here, all others are handled
	 * in <code>Dialog</code>
	 * 
	 * @see Dialog#buttonPressed(int)
	 */
	protected void buttonPressed(int buttonId) {
		if (buttonId == ID_LAUNCH_BUTTON) {
			handleLaunchPressed();
		} else {
			super.buttonPressed(buttonId);
		}
	}

	/**
	 * @see Dialog#createContents(Composite)
	 */
	protected Control createContents(Composite parent) {
		Control contents = super.createContents(parent);
		getLaunchManager().addLaunchConfigurationListener(this);
		displayFirstConfig();
		return contents;
	}
	
	/**
	 * Display the first configuration in this dialog.
	 */
	protected void displayFirstConfig() {
		IStructuredSelection selection = StructuredSelection.EMPTY;
		if (fFirstConfig instanceof ILaunchConfigurationWorkingCopy) {
			try {
				ILaunchConfigurationType firstConfigType = fFirstConfig.getType();
				selection = new StructuredSelection(firstConfigType);
				setIgnoreSelectionChanges(true);
				getTreeViewer().setSelection(selection);			
				setIgnoreSelectionChanges(false);
				setLaunchConfiguration(fFirstConfig, false);
			} catch (CoreException ce) {
				DebugUIPlugin.log(ce);
			}
		} else if (fFirstConfig instanceof ILaunchConfiguration) {
			selection = new StructuredSelection(fFirstConfig);			
			getTreeViewer().setSelection(selection);			
		} else {
			getTreeViewer().setSelection(selection);
		}
	}
	
	/**
	 * Determine the first configuration for this dialog.  If this configuration verifies
	 * and the 'single-click launching' preference is turned on, launch the configuration
	 * WITHOUT realizing the dialog.  Otherwise, call super.open(), which will realize the
	 * dialog and display the first configuration.  If single-click launching was successful,
	 * this method returns <code>ILaunchConfigurationDialog.SINGLE_CLICK_LAUNCHED</code>.
	 * 
	 * @see Window#open()
	 */
	public int open() {		
		try {
			if (isAttemptSingleClickLaunch()) {
				// single click
				fFirstConfig = determineConfigFromContext();
				if (fFirstConfig != null) {
					if (fFirstConfig instanceof ILaunchConfigurationWorkingCopy) {
						setWorkingCopy((ILaunchConfigurationWorkingCopy) fFirstConfig);
					} else {
						fUnderlyingConfig = fFirstConfig;
					}
					doLaunch(fFirstConfig);
					return ILaunchConfigurationDialog.SINGLE_CLICK_LAUNCHED;					
				}
			} else if (getInitialConfigType() != null) {
				fFirstConfig = createConfigOfType(getInitialConfigType());
			} else {
				String memento = getPreferenceStore().getString(IDebugPreferenceConstants.PREF_LAST_LAUNCH_CONFIGURATION_SELECTION);
				if (memento.length() < 1) {
					// last launch
					LaunchConfigurationHistoryElement history = DebugUIPlugin.getDefault().getLastLaunch();
					if (history != null) {
						fFirstConfig = history.getLaunchConfiguration();
					}
				} else {
					// last selection
					try {
						fFirstConfig = getLaunchManager().getLaunchConfiguration(memento);
					} catch (CoreException e) {
						// do not warn when restoring selection
						DebugUIPlugin.log(e);
					}
				}
			}	
		} catch (CoreException e) {
			DebugUIPlugin.errorDialog(DebugUIPlugin.getShell(), "Launch Configuration Error", "Exception occurred processing launch configuration. See log for more information.", e);
		}
		return super.open();
	}
	
	/**
	 * @see Window#close()
	 */
	public boolean close() {
		getLaunchManager().removeLaunchConfigurationListener(this);
		return super.close();
	}
	
	/**
	 * @see org.eclipse.jface.dialogs.Dialog#createDialogArea(Composite)
	 */
	protected Control createDialogArea(Composite parent) {
		GridData gd;
		Composite dialogComp = (Composite)super.createDialogArea(parent);
		Composite topComp = new Composite(dialogComp, SWT.NONE);
		GridLayout topLayout = new GridLayout();
		topLayout.numColumns = 2;
		topLayout.marginHeight = 5;
		topLayout.marginWidth = 0;
		topComp.setLayout(topLayout);

		// Set the things that TitleAreaDialog takes care of
		setTitle("Create, manage, and run launch configurations");
		setMessage("Ready to launch");
		setModeLabelState();

		// Build the launch configuration selection area
		// and put it into the composite.
		Composite launchConfigSelectionArea = createLaunchConfigurationSelectionArea(topComp);
		gd = new GridData(GridData.FILL_VERTICAL);
		launchConfigSelectionArea.setLayoutData(gd);
	
		// Build the launch configuration edit area
		// and put it into the composite.
		Composite launchConfigEditArea = createLaunchConfigurationEditArea(topComp);
		gd = new GridData(GridData.FILL_BOTH);
		launchConfigEditArea.setLayoutData(gd);
			
		// Build the separator line
		Label separator = new Label(topComp, SWT.HORIZONTAL | SWT.SEPARATOR);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 2;
		separator.setLayoutData(gd);
		
		return dialogComp;
	}
	
	/**
	 * Determine and return an <code>ILaunchConfiguration</code> from the current context.
	 * If the context is itself an ILaunchConfiguration, this is returned.  Otherwise,
	 * an <code>ILaunchConfigurationWorkingCopy</code> is created and initialized from 
	 * the context if possible.
	 */
	protected ILaunchConfiguration determineConfigFromContext() throws CoreException {
		Object workbenchSelection = getContext();
		if (workbenchSelection == null) {
			return null;
		}
		
		if (workbenchSelection instanceof ILaunchConfiguration) {
			return (ILaunchConfiguration) workbenchSelection;
		}
		
		IResource res = getResourceContext();
		if (res == null) {
			IEditorInput activeEditorInput= WorkbenchPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor().getEditorInput();
			if (activeEditorInput != null) {
				setContext(activeEditorInput);
				res= getResourceContext();
			}
		}
		ILaunchConfiguration def = null;
		if (res != null) {
			ILaunchConfigurationType type = determineConfigTypeFromContext();
			if (type != null) {
				try {
					def = getLaunchManager().getDefaultLaunchConfiguration(res, type.getIdentifier());
				} catch (CoreException e) {
					// hide the exception and return no default
					DebugUIPlugin.log(e);
				}
			}
		}
		if (def != null) {
			return def;
		}
		
		def = createConfigFromContext();
		if (res != null && def != null) {
			getLaunchManager().setDefaultLaunchConfiguration(res, def);
		}
		return def;
	}
	
	/**
	 * Something other than an <code>ILaunchConfiguration</code> was selected in
	 * the workbench, so try to determine an <code>ILaunchConfigurationType</code>
	 * from the selection, then create a new working copy of that type, initialize
	 * its default values, set this new launch configuration so that the edit area
	 * tabs get populated, and finally make sure the config type is selected in the 
	 * configuration tree.
	 */
	protected ILaunchConfiguration createConfigFromContext() {
		ILaunchConfigurationType configType = determineConfigTypeFromContext();
		if (configType == null) {
			return null;
		}
		return createConfigOfType(configType);
	}
	
	/**
	 * Create and return a launch configuration working copy of the specified type.
	 * This method is intended to be called before the UI has been realized, such as in
	 * the case of single-click launching or creating a config for an initial configuration
	 * type.
	 */
	protected ILaunchConfiguration createConfigOfType(ILaunchConfigurationType configType) {		
		ILaunchConfigurationWorkingCopy workingCopy = null;
		try {
			workingCopy = configType.newInstance(null, generateUniqueNameFrom(DEFAULT_NEW_CONFIG_NAME));
		} catch (CoreException ce) {
			DebugUIPlugin.log(ce);
			return null;
		}

		ILaunchConfiguration config = null;
		try {
	 		ILaunchConfigurationTabGroup group= createGroup(configType);
	 		group.setDefaults(workingCopy);
	 		group.dispose();
	 		if (workingCopy.getName().trim().length() == 0) {
	 			// assign a name if not done yet
	 			IResource res = getResourceContext();
	 			String name = "";
	 			if (res != null) {
	 				name = res.getName();
	 			}
	 			name = generateName(name);
	 			workingCopy.rename(name);
	 		}
	 		config = workingCopy.doSave();
		} catch (CoreException e) {
			DebugUIPlugin.log(e);
			return null;
		}
		
		return config;
	}
	
	/**
	 * Returns tab group for the given type of launch configuration.
	 * Tabs are initialized to be contained in this dialog.
	 * 
	 * @exception CoreException if unable to instantiate a tab group
	 */
	protected ILaunchConfigurationTabGroup createGroup(ILaunchConfigurationType configType) throws CoreException {
		// initialize with default values
		// build the new tabs
 		ILaunchConfigurationTabGroup group = LaunchConfigurationPresentationManager.getDefault().getTabGroup(configType);
 		group.createTabs(this, getMode());
 		ILaunchConfigurationTab[] tabs = group.getTabs();
 		for (int i = 0; i < tabs.length; i++) {
 			tabs[i].setLaunchConfigurationDialog(this);
 		}
 		return group;
	}
	
	/**
	 * Attempt to determine the launch config type most closely associated
	 * with the current workbench selection.
	 */
	protected ILaunchConfigurationType determineConfigTypeFromContext() {		
		IResource resource = getResourceContext();
		ILaunchConfigurationType type = null;
		if (resource != null) {
			type = getLaunchManager().getDefaultLaunchConfigurationType(resource, false);
		}
		return type;		
	}
	
	/**
	 * Returns the selected resoruce context from the workbench,
	 * or <code>null</code>
	 */
	protected IResource getResourceContext() {
		IResource resource = null;
		Object workbenchSelection = getContext();
		if (workbenchSelection instanceof IResource) {
			resource = (IResource)workbenchSelection;
		} else if (workbenchSelection instanceof IAdaptable) {
			resource = (IResource) ((IAdaptable)workbenchSelection).getAdapter(IResource.class);
		}
		return resource;		
	}

	
	/**
	 * Set the title area image based on the mode this dialog was initialized with
	 */
	protected void setModeLabelState() {
		Image image;
		if (getMode().equals(ILaunchManager.DEBUG_MODE)) {
			image = DebugUITools.getImage(IDebugUIConstants.IMG_WIZBAN_DEBUG);
		} else {
			image = DebugUITools.getImage(IDebugUIConstants.IMG_WIZBAN_RUN);
		}
		setTitleImage(image);
	}
	
	/**
	 * Convenience method to set the selection on the configuration tree.
	 */
	protected void setTreeViewerSelection(ISelection selection) {
		getTreeViewer().setSelection(selection);
	}
	
	private void setLastSavedName(String lastSavedName) {
		this.fLastSavedName = lastSavedName;
	}

	private String getLastSavedName() {
		return fLastSavedName;
	}
	
	/**
	 * Update buttons and message.
	 */
	protected void refreshStatus() {
		updateButtons();
		updateMessage();
	}
	
	/**
	 * Verify the working copy
	 */
	protected void verify() throws CoreException {
		verifyStandardAttributes();	
	}
	
	/**
	 * Verify the attributes common to all launch configuration.
	 * To be consistent with <code>ILaunchConfiguration.verify</code>,
	 * indicate failure by throwing a <code>CoreException</code>.
	 */
	protected void verifyStandardAttributes() throws CoreException {
		verifyName();
	}
	
	/**
	 * Verify that the launch configuration name is valid.
	 */
	protected void verifyName() throws CoreException {
		String currentName = getNameTextWidget().getText().trim();

		// If there is no name, complain
		if (currentName.length() < 1) {
			throw new CoreException(new Status(IStatus.ERROR,
												 DebugUIPlugin.getDefault().getDescriptor().getUniqueIdentifier(),
												 0,
												 "Name required for launch configuration.",
												 null));			
		}

		// If the name hasn't changed from the last saved name, do nothing
		if (currentName.equals(getLastSavedName())) {
			return;
		}	
		
		// See if name contains any 'illegal' characters
		IStatus status = ResourcesPlugin.getWorkspace().validateName(currentName, IResource.FILE);
		if (status.getCode() != IStatus.OK) {
			throw new CoreException(new Status(IStatus.ERROR,
												 DebugUIPlugin.getDefault().getDescriptor().getUniqueIdentifier(),
												 0,
												 status.getMessage(),
												 null));									
		}			
		
		// Otherwise, if there's already a config with the same name, complain
		if (getLaunchManager().isExistingLaunchConfigurationName(currentName)) {
			throw new CoreException(new Status(IStatus.ERROR,
												 DebugUIPlugin.getDefault().getDescriptor().getUniqueIdentifier(),
												 0,
												 "Launch configuration already exists with this name.",
												 null));						
		}						
	}
	
	/**
	 * If the name is valid, rename the current launch configuration.  Otherwise, show an
	 * appropriate error message.
	 */
	protected void updateConfigFromName() {
		if (getLaunchConfiguration() != null) {
			try {
				verifyName();
			} catch (CoreException ce) {
				refreshStatus();
				return;				
			}
						
			getLaunchConfiguration().rename(getNameTextWidget().getText().trim());
			refreshStatus();
		}
	}
	
	protected Display getDisplay() {
		return getShell().getDisplay();
	}
		
	/**
	 * Creates the launch configuration selection area of the dialog.
	 * This area displays a tree of launch configrations that the user
	 * may select, and allows users to create new configurations, and
	 * delete and copy existing configurations.
	 * 
	 * @return the composite used for launch configuration selection
	 */ 
	protected Composite createLaunchConfigurationSelectionArea(Composite parent) {
		Composite c = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 3;
		layout.marginHeight = 0;
		layout.marginWidth = 5;
		c.setLayout(layout);
		
		GridData gd;
		
		TreeViewer tree = new TreeViewer(c);
		gd = new GridData(GridData.FILL_BOTH);
		gd.horizontalSpan = 3;
		gd.widthHint = 200;
		gd.heightHint = 375;
		tree.getControl().setLayoutData(gd);
		tree.setContentProvider(new LaunchConfigurationContentProvider());
		tree.setLabelProvider(DebugUITools.newDebugModelPresentation());
		tree.setSorter(new WorkbenchViewerSorter());
		setTreeViewer(tree);
		tree.addSelectionChangedListener(this);
		tree.setInput(ResourcesPlugin.getWorkspace().getRoot());
		tree.expandAll();
		tree.addDoubleClickListener(this);
		
		Button newButton = SWTUtil.createPushButton(c, "Ne&w", null);
		setNewButton(newButton);
		newButton.addSelectionListener(
			new SelectionAdapter() { 
				public void widgetSelected(SelectionEvent event) {
					handleNewPressed();
				}
			}
		);				
		
		Button deleteButton = SWTUtil.createPushButton(c, "Dele&te", null);
		setDeleteButton(deleteButton);
		deleteButton.addSelectionListener(
			new SelectionAdapter() { 
				public void widgetSelected(SelectionEvent event) {
					handleDeletePressed();
				}
			}
		);			
		
		Button copyButton = SWTUtil.createPushButton(c, "Cop&y", null);
		setCopyButton(copyButton);		
		copyButton.addSelectionListener(
			new SelectionAdapter() { 
				public void widgetSelected(SelectionEvent event) {
					handleCopyPressed();
				}
			}
		);			
		
		return c;
	}	
	
	/**
	 * Creates the launch configuration edit area of the dialog.
	 * This area displays the name of the launch configuration
	 * currently being edited, as well as a tab folder of tabs
	 * that are applicable to the launch configuration.
	 * 
	 * @return the composite used for launch configuration editing
	 */ 
	protected Composite createLaunchConfigurationEditArea(Composite parent) {
		Composite c = new Composite(parent, SWT.NONE);
		setEditArea(c);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginHeight = 0;
		layout.marginWidth = 5;
		c.setLayout(layout);
		
		GridData gd;
		
		Label nameLabel = new Label(c, SWT.HORIZONTAL | SWT.LEFT);
		nameLabel.setText("&Name:");
		gd = new GridData(GridData.BEGINNING);
		nameLabel.setLayoutData(gd);
		
		Text nameText = new Text(c, SWT.SINGLE | SWT.BORDER);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		nameText.setLayoutData(gd);
		setNameTextWidget(nameText);
		
		getNameTextWidget().addModifyListener(
			new ModifyListener() {
				public void modifyText(ModifyEvent e) {
					updateConfigFromName();
				}
			}
		);		
		
		Label spacer = new Label(c, SWT.NONE);
		gd = new GridData();
		gd.horizontalSpan = 2;
		spacer.setLayoutData(gd);
		
		TabFolder tabFolder = new TabFolder(c, SWT.NONE);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 2;
		gd.heightHint = 375;
		gd.widthHint = 375;
		tabFolder.setLayoutData(gd);
		setTabFolder(tabFolder);
		getTabFolder().addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent evt) {
				handleTabSelected();
			}
		});
		
		Button saveButton = new Button(c, SWT.PUSH);
		saveButton.setText("&Apply");
		gd = new GridData(GridData.HORIZONTAL_ALIGN_END);
		gd.horizontalSpan = 2;
		saveButton.setLayoutData(gd);
		setSaveButton(saveButton);
		SWTUtil.setButtonDimensionHint(saveButton);
		getSaveButton().addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent evt) {
				handleSavePressed();
			}
		});
		
		return c;
	}	
	
	/**
	 * @see Dialog#createButtonBar(Composite)
	 */
	protected Control createButtonBar(Composite parent) {
		Composite composite= new Composite(parent, SWT.NULL);
		GridLayout layout= new GridLayout();
		layout.numColumns= 2;
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		GridLayout pmLayout = new GridLayout();
		pmLayout.numColumns = 2;
		setProgressMonitorPart(new ProgressMonitorPart(composite, pmLayout, PROGRESS_INDICATOR_HEIGHT));
		getProgressMonitorPart().setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		getProgressMonitorPart().setVisible(false);

		return super.createButtonBar(composite);
	}
	
	/**
	 * Sets the title for the dialog and establishes the help context.
	 * 
	 * @see org.eclipse.jface.window.Window#configureShell(Shell);
	 */
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Launch Configurations");
		WorkbenchHelp.setHelp(
			shell,
			IDebugHelpContextIds.LAUNCH_CONFIGURATION_DIALOG);
	}
	
	/**
	 * Sets the tree viewer used to display launch configurations.
	 * 
	 * @param viewer the tree viewer used to display launch
	 *  configurations
	 */
	private void setTreeViewer(TreeViewer viewer) {
		fConfigTree = viewer;
	}
	
	/**
	 * Returns the tree viewer used to display launch configurations.
	 * 
	 * @param the tree viewer used to display launch configurations
	 */
	protected TreeViewer getTreeViewer() {
		return fConfigTree;
	}
	
	protected IStructuredSelection getTreeViewerSelection() {
		return (IStructuredSelection)getTreeViewer().getSelection();
	}
	
	protected Object getTreeViewerFirstSelectedElement() {
		IStructuredSelection selection = getTreeViewerSelection();
		if (selection == null) {
			return null;
		}
		return selection.getFirstElement();
	}
		
	/**
	 * Content provider for launch configuration tree
	 */
	class LaunchConfigurationContentProvider implements ITreeContentProvider {
		
		/**
		 * Actual launch configurations have no children.  Launch configuration types have
		 * all configurations of that type as children, minus any configurations that are 
		 * private or were autosaved.
		 * 
		 * @see ITreeContentProvider#getChildren(Object)
		 */
		public Object[] getChildren(Object parentElement) {
			if (parentElement instanceof ILaunchConfiguration) {
				return EMPTY_ARRAY;
			} else if (parentElement instanceof ILaunchConfigurationType) {
				try {
					ILaunchConfigurationType type = (ILaunchConfigurationType)parentElement;
					ILaunchConfiguration[] allConfigs = getLaunchManager().getLaunchConfigurations(type);
					ArrayList filteredConfigs = new ArrayList(allConfigs.length);
					for (int i = 0; i < allConfigs.length; i++) {
						ILaunchConfiguration config = allConfigs[i];
						if (config.getAttribute(IDebugUIConstants.ATTR_PRIVATE, false)) {
							continue;
						}
						filteredConfigs.add(config);
					}
					return filteredConfigs.toArray();
				} catch (CoreException e) {
					DebugUIPlugin.errorDialog(getShell(), "Error", "An exception occurred while retrieving launch configurations.", e);
				}
			} else {
				return getLaunchManager().getLaunchConfigurationTypes();
			}
			return EMPTY_ARRAY;
		}

		/**
		 * @see ITreeContentProvider#getParent(Object)
		 */
		public Object getParent(Object element) {
			if (element instanceof ILaunchConfiguration) {
				if (!((ILaunchConfiguration)element).exists()) {
					return null;
				}
				try {
					return ((ILaunchConfiguration)element).getType();
				} catch (CoreException e) {
					DebugUIPlugin.errorDialog(getShell(), "Error", "An exception occurred while retrieving launch configurations.", e);
				}
			} else if (element instanceof ILaunchConfigurationType) {
				return ResourcesPlugin.getWorkspace().getRoot();
			}
			return null;
		}

		/**
		 * @see ITreeContentProvider#hasChildren(Object)
		 */
		public boolean hasChildren(Object element) {
			if (element instanceof ILaunchConfiguration) {
				return false;
			} else {
				return getChildren(element).length > 0;
			}
		}

		/**
		 * Return only the launch configuration types that support the current mode.
		 * @see IStructuredContentProvider#getElements(Object)
		 */
		public Object[] getElements(Object inputElement) {
			ILaunchConfigurationType[] allTypes = getLaunchManager().getLaunchConfigurationTypes();
			ArrayList list = new ArrayList(allTypes.length);
			String mode = getMode();
			for (int i = 0; i < allTypes.length; i++) {
				if (allTypes[i].supportsMode(mode)) {
					list.add(allTypes[i]);
				}
			}			
			return list.toArray();
		}

		/**
		 * @see IContentProvider#dispose()
		 */
		public void dispose() {
		}

		/**
		 * @see IContentProvider#inputChanged(Viewer, Object, Object)
		 */
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}

	}
	
	/**
	 * Returns the launch manager.
	 * 
	 * @return the launch manager
	 */
	protected ILaunchManager getLaunchManager() {
		return DebugPlugin.getDefault().getLaunchManager();
	}

	/**
	 * Returns whether this dialog is currently open
	 */
	protected boolean isVisible() {
		return getTreeViewer() != null;
	}	
		
	/**
	 * Notification that selection has changed in the launch configuration tree.
	 * <p>
	 * If the currently displayed configuration is not saved,
	 * prompt for saving before moving on to the new selection.
	 * </p>
	 * 
	 * @param event selection changed event
	 */
 	public void selectionChanged(SelectionChangedEvent event) {
 		
 		// Ignore selectionChange events that occur while saving
 		if (ignoreSelectionChanges()) {
 			return;
 		}
 		
 		// Get the selection		
 		IStructuredSelection selection = (IStructuredSelection)event.getSelection();
 		if (selection.isEmpty()) {
 			getEditArea().setVisible(false);
 			setWorkingCopy(null);
 			setSelectedTreeObject(null);
 			updateButtons();
 			return;
 		}
 		
 		Object firstSelectedElement = selection.getFirstElement();		
 		boolean singleSelection = selection.size() == 1;
		boolean configSelected = firstSelectedElement instanceof ILaunchConfiguration;

 		// If selection is the same, don't bother
 		Object obj = getSelectedTreeObject();
 		if (singleSelection && obj != null && obj.equals(firstSelectedElement)) {
 			getEditArea().setVisible(obj instanceof ILaunchConfiguration);
 			return;
 		}
 		
		// Take care of any unsaved changes.  If the user aborts, reset selection
		// to whatever it was previously selected
		boolean canReplaceConfig = canReplaceCurrentConfig();
 		if (!canReplaceConfig) {
 			StructuredSelection prevSelection;
 			Object selectedTreeObject = getSelectedTreeObject();
			if (selectedTreeObject == null) {
				prevSelection = StructuredSelection.EMPTY;
			} else {
				prevSelection = new StructuredSelection(selectedTreeObject);
			}
 			setTreeViewerSelection(prevSelection);
 			return;
 		}			
 				 		 		 
		// If a config is selected, update the edit area for it, if a config type is
		// selected, clear the edit area
 		if (singleSelection && configSelected) {
 			ILaunchConfiguration config = (ILaunchConfiguration) firstSelectedElement; 			
 			setLastSavedName(config.getName());
 			setLaunchConfiguration(config, false);
 		} else if (singleSelection && firstSelectedElement instanceof ILaunchConfigurationType) {
			if (canReplaceConfig) {
				clearLaunchConfiguration();
				getEditArea().setVisible(false);
				disposeExistingTabs();
			}
 		} else {
 			// multi-selection
 			clearLaunchConfiguration();
 			getEditArea().setVisible(false);
 		}
 		
 		updateButtons();
 		if (singleSelection) {
	 		setSelectedTreeObject(firstSelectedElement);
 		} else {
 			setSelectedTreeObject(null);
 		}
 	}
 	
 	protected void setProgressMonitorPart(ProgressMonitorPart part) {
 		fProgressMonitorPart = part;
 	}
 	
 	protected ProgressMonitorPart getProgressMonitorPart() {
 		return fProgressMonitorPart;
 	}
 	
 	/**
 	 * Sets the 'new' button.
 	 * 
 	 * @param button the 'new' button.
 	 */	
 	private void setNewButton(Button button) {
 		fNewButton = button;
 	} 	
 	
  	/**
 	 * Returns the 'new' button
 	 * 
 	 * @return the 'new' button
 	 */
 	protected Button getNewButton() {
 		return fNewButton;
 	}
 	
 	/**
 	 * Sets the 'delete' button.
 	 * 
 	 * @param button the 'delete' button.
 	 */	
 	private void setDeleteButton(Button button) {
 		fDeleteButton = button;
 	} 	
 	
 	/**
 	 * Returns the 'delete' button
 	 * 
 	 * @return the 'delete' button
 	 */
 	protected Button getDeleteButton() {
 		return fDeleteButton;
 	}
 	 	
 	/**
 	 * Sets the 'copy' button.
 	 * 
 	 * @param button the 'copy' button.
 	 */	
 	private void setCopyButton(Button button) {
 		fCopyButton = button;
 	} 	
 	
 	/**
 	 * Returns the 'copy' button
 	 * 
 	 * @return the 'copy' button
 	 */
 	protected Button getCopyButton() {
 		return fCopyButton;
 	} 	
 	
 	/**
 	 * Sets the configuration to display/edit.
 	 * Updates the tab folder to contain the appropriate pages.
 	 * Sets all configuration-related state appropriately.
 	 * 
 	 * @param config the launch configuration to display/edit
 	 * @param init whether to initialize the config with default values
 	 */
 	protected void setLaunchConfiguration(ILaunchConfiguration config, boolean init) {
		try {
			
			// turn on initializing flag to ignore message updates
			setInitializingTabs(true);
			
			getEditArea().setVisible(true);
			setTabsForConfigType(config.getType());
			
			if (config.isWorkingCopy()) {
		 		setWorkingCopy((ILaunchConfigurationWorkingCopy)config);
			} else {
				setWorkingCopy(config.getWorkingCopy());
			}
			fUnderlyingConfig = getLaunchConfiguration().getOriginal();
	 		
	 		// update the name field before to avoid verify error 
	 		getNameTextWidget().setText(config.getName());	 		
	 			 		
	 		// Set the defaults for all tabs before any are initialized
	 		// so that every tab can see ALL the default values
	 		if (init) {
				getTabGroup().setDefaults(getLaunchConfiguration());
	 		}

	 		// update the tabs with the new working copy	 		
			getTabGroup().initializeFrom(getLaunchConfiguration());
	 		
	 		// update the name field after in case client changed it 
	 		getNameTextWidget().setText(config.getName());
	 				
	 		// turn off initializing flag to update message
			setInitializingTabs(false);
			
	 		refreshStatus();
	 		
		} catch (CoreException ce) {
 			DebugUIPlugin.errorDialog(getShell(), "Error", "Exception occurred setting launch configuration", ce);
 			clearLaunchConfiguration();
 			return;					
		}
 	} 
 	
 	/**
 	 * Clears the configuration being shown/edited.   
 	 * Resets all configuration-related state.
 	 */
 	protected void clearLaunchConfiguration() {
 		setWorkingCopy(null);
 		fUnderlyingConfig = null;
 		setLastSavedName(null);
 		getNameTextWidget().setText(""); 
 		refreshStatus();
 	}
 	
 	/**
 	 * Populate the tabs in the configuration edit area to be appropriate to the current
 	 * launch configuration type.
 	 */
 	protected void setTabsForConfigType(ILaunchConfigurationType configType) {		
 		if (getTabType() != null && getTabType().equals(configType)) {
 			return;
 		}
 		
 		// avoid flicker
 		getEditArea().setVisible(false);
 		
		// dispose the current tabs
		disposeExistingTabs();

		// build the new tabs
 		ILaunchConfigurationTabGroup group = null;
 		try {
	 		group = createGroup(configType);
 		} catch (CoreException ce) {
 			DebugUIPlugin.errorDialog(getShell(), "Error", "Exception occurred creating launch configuration tabs.",ce);
 			return;
 		}
 		
 		ILaunchConfigurationTab[] tabs = group.getTabs();
 		for (int i = 0; i < tabs.length; i++) {
 			TabItem tab = new TabItem(getTabFolder(), SWT.NONE);
 			String name = tabs[i].getName();
 			if (name == null) {
 				name = "unspecified";
 			}
 			tab.setText(name);
 			tabs[i].createControl(tab.getParent());
 			Control control = tabs[i].getControl();
 			if (control != null) {
	 			tab.setControl(control);
 			}
 		}
 		setTabGroup(group);	
 		setTabType(configType);
 		getEditArea().setVisible(true);
 	}
 	
 	protected void disposeExistingTabs() {
		TabItem[] oldTabs = getTabFolder().getItems();
		if (getTabGroup() != null) {
			getTabGroup().dispose();
		}
		for (int i = 0; i < oldTabs.length; i++) {
			oldTabs[i].dispose();
		} 		
		setTabGroup(null);
		setTabType(null);
 	}
 	
 	/**
 	 * Sets the current launch configuration that is being
 	 * displayed/edited.
 	 */
 	protected void setWorkingCopy(ILaunchConfigurationWorkingCopy workingCopy) {
 		fWorkingCopy = workingCopy;
 	}
 	
 	protected boolean isWorkingCopyDirty() {
 		ILaunchConfigurationWorkingCopy workingCopy = getLaunchConfiguration();
 		if (workingCopy == null) {
 			return false;
 		}
 		
 		// Working copy hasn't been saved
 		if (workingCopy.getOriginal() == null) {
 			return true;
 		}
 		
 		// Name has changed.  Normally, this would be caught in the 'contentsEqual'
 		// check below, however there are some circumstances where this fails, such as
 		// when the name is invalid
 		if (isNameDirty()) {
 			return true;
 		}

 		updateWorkingCopyFromPages();
 		ILaunchConfiguration original = workingCopy.getOriginal();
 		return !original.contentsEqual(workingCopy);
 	}
 	
 	/**
 	 * Return <code>true</code> if the name has been modified since the last time it was saved.
 	 */
 	protected boolean isNameDirty() {
 		String currentName = getNameTextWidget().getText().trim();
 		return !currentName.equals(getLastSavedName());
 	}
 	
 	protected void setContext(Object context) {
 		fContext = context;
 	}
 	
 	protected Object getContext() {
 		return fContext;
 	}
 	 	
 	protected void setMode(String mode) {
 		fMode = mode;
 	}
 	
 	/** 
 	 * @see ILaunchConfigurationDialog#getMode()
 	 */
 	public String getMode() {
 		return fMode;
 	}
 	
	/**
	 * Sets the text widget used to display the name
	 * of the configuration being displayed/edited
	 * 
	 * @param widget the text widget used to display the name
	 *  of the configuration being displayed/edited
	 */
	private void setNameTextWidget(Text widget) {
		fNameText = widget;
	}
	
	/**
	 * Returns the text widget used to display the name
	 * of the configuration being displayed/edited
	 * 
	 * @return the text widget used to display the name
	 *  of the configuration being displayed/edited
	 */
	protected Text getNameTextWidget() {
		return fNameText;
	} 
	
 	/**
 	 * Sets the 'save' button.
 	 * 
 	 * @param button the 'save' button.
 	 */	
 	private void setSaveButton(Button button) {
 		fSaveButton = button;
 	}
 	
 	/**
 	 * Returns the 'save' button
 	 * 
 	 * @return the 'save' button
 	 */
 	protected Button getSaveButton() {
 		return fSaveButton;
 	}	
 	
 	/**
 	 * Sets the tab folder
 	 * 
 	 * @param folder the tab folder
 	 */	
 	private void setTabFolder(TabFolder folder) {
 		fTabFolder = folder;
 	}
 	
 	/**
 	 * Returns the tab folder
 	 * 
 	 * @return the tab folder
 	 */
 	protected TabFolder getTabFolder() {
 		return fTabFolder;
 	}	 	
 	
 	/**
 	 * Sets the current tab group being displayed
 	 * 
 	 * @param group the current tab group being displayed
 	 */
 	private void setTabGroup(ILaunchConfigurationTabGroup group) {
 		fTabGroup = group;
 	}
 	
 	/**
 	 * Returns the current tab group
 	 * 
 	 * @return the current tab group, or <code>null</code> if none
 	 */
 	public ILaunchConfigurationTabGroup getTabGroup() {
 		return fTabGroup;
 	}
 	
 	/**
 	 * @see ILaunchConfigurationDialog#getTabs()
 	 */
 	public ILaunchConfigurationTab[] getTabs() {
 		if (getTabGroup() == null) {
 			return null;
 		} else {
 			return getTabGroup().getTabs();
 		}
 	} 	
 	
	/**
	 * @see ILaunchConfigurationListener#launchConfigurationAdded(ILaunchConfiguration)
	 */
	public void launchConfigurationAdded(ILaunchConfiguration configuration) {
		setIgnoreSelectionChanges(true);
		try {
			setWorkingCopy(configuration.getWorkingCopy());
			fUnderlyingConfig = configuration;
			setSelectedTreeObject(configuration);
		} catch (CoreException e) {
			DebugUIPlugin.log(e);
		}
		getTreeViewer().refresh();	
		updateButtons();
		setIgnoreSelectionChanges(false);
	}

	/**
	 * @see ILaunchConfigurationListener#launchConfigurationChanged(ILaunchConfiguration)
	 */
	public void launchConfigurationChanged(ILaunchConfiguration configuration) {
	}

	/**
	 * @see ILaunchConfigurationListener#launchConfigurationRemoved(ILaunchConfiguration)
	 */
	public void launchConfigurationRemoved(ILaunchConfiguration configuration) {
		getTreeViewer().remove(configuration);		
	}
	
	protected void setIgnoreSelectionChanges(boolean ignore) {
		fIgnoreSelectionChanges = ignore;
	}
	
	protected boolean ignoreSelectionChanges() {
		return fIgnoreSelectionChanges;
	}
	
	/**
	 * Return whether the current configuration can be replaced.  This involves determining
	 * if it is dirty, and if it is, asking the user what to do.
	 */
	protected boolean canReplaceCurrentConfig() {		
		// If there is no working copy, there's no problem, return true
		ILaunchConfigurationWorkingCopy workingCopy = getLaunchConfiguration();
		if (workingCopy == null) {
			return true;
		}
		
		if (isWorkingCopyDirty()) {
			return showUnsavedChangesDialog();
		} else {
			return true;
		}
	}
	
	/**
	 * Show the user a dialog appropriate to whether the unsaved changes in the current config
	 * can be saved or not.  Return <code>true</code> if the user indicated that they wish to replace
	 * the current config, either by saving changes or by discarding the, return <code>false</code>
	 * otherwise.
	 */
	protected boolean showUnsavedChangesDialog() {
		if (canSaveConfig()) {
			return showSaveChangesDialog();
		} else {
			return showDiscardChangesDialog();
		}
	}
	
	/**
	 * Create and return a dialog that asks the user whether they want to save
	 * unsaved changes.  Return <code>true </code> if they chose to save changes,
	 * <code>false</code> otherwise.
	 */
	protected boolean showSaveChangesDialog() {
		StringBuffer buffer = new StringBuffer("The configuration \"");
		buffer.append(getLaunchConfiguration().getName());
		buffer.append("\" has unsaved changes.  Do you wish to save them?");
		MessageDialog dialog = new MessageDialog(getShell(), 
												 "Save changes?",
												 null,
												 buffer.toString(),
												 MessageDialog.QUESTION,
												 new String[] {"Yes", "No", "Cancel"},
												 0);
		// If user clicked 'Cancel' or closed dialog, return false
		int selectedButton = dialog.open();
		if ((selectedButton < 0) || (selectedButton == 2)) {
			return false;
		}
		
		// If they hit 'Yes', save the working copy 
		if (selectedButton == 0) {
			handleSavePressed();
		}
		
		return true;
	}
	
	/**
	 * Create and return a dialog that asks the user whether they want to discard
	 * unsaved changes.  Return <code>true</code> if they chose to discard changes,
	 * <code>false</code> otherwise.
	 */
	protected boolean showDiscardChangesDialog() {
		StringBuffer buffer = new StringBuffer("The configuration \"");
		buffer.append(getLaunchConfiguration().getName());
		buffer.append("\" has unsaved changes that CANNOT be saved.  Do you wish to discard them?");
		MessageDialog dialog = new MessageDialog(getShell(), 
												 "Discard changes?",
												 null,
												 buffer.toString(),
												 MessageDialog.QUESTION,
												 new String[] {"Yes", "No"},
												 1);
		// If user clicked 'Yes', return true
		int selectedButton = dialog.open();
		if (selectedButton == 0) {
			return true;
		}
		return false;
	}
	
	/**
	 * Return <code>true</code> if the current configuration can be saved, <code>false</code>
	 * otherwise.  Note this is NOT the same thing as the config simply being valid.  It is
	 * possible to save a config that does not validate.  This method determines whether the
	 * config can be saved without causing a serious error.  For example, a shared config that
	 * has no specified location would cause this method to return <code>false</code>.
	 */
	protected boolean canSaveConfig() {
		try {
			verifyName();
			return true;
		} catch (CoreException ce) {
			return false;
		}
	}

	/**
	 * Notification the 'new' button has been pressed
	 */
	protected void handleNewPressed() {
		
		// Take care of any unsaved changes
		if (!canReplaceCurrentConfig()) {
			return;
		}
		
		// Retrieve the config type from the current selection and construct a config from it
		try {
			ILaunchConfigurationType configType = null;
			Object obj = getTreeViewerFirstSelectedElement();
			if (obj instanceof ILaunchConfiguration) {
				configType = ((ILaunchConfiguration)obj).getType();
			} else {
				configType = (ILaunchConfigurationType)obj;
			}
			constructNewConfig(configType);
			getTreeViewer().setSelection(new StructuredSelection(fUnderlyingConfig));
		} catch(CoreException ce) {
			DebugUIPlugin.errorDialog(getShell(), "Error", "Exception getting configuration type for new launch configuration.", ce);
 			clearLaunchConfiguration();
		}		
	}	
	
	/** 
	 * If a config type is selected, create a new config of that type initialized to 
	 * fWorkbenchSelection.  If a config is selected, create of new config of the
	 * same type as the selected config.
	 * protected void constructNewConfig() {
	 */
	protected void constructNewConfig(ILaunchConfigurationType configType) {	
		try {
			ILaunchConfigurationWorkingCopy wc = configType.newInstance(null, generateUniqueNameFrom(DEFAULT_NEW_CONFIG_NAME));
			setLastSavedName(null);
			setLaunchConfiguration(wc, true);
			doSave();
		} catch (CoreException ce) {
			DebugUIPlugin.errorDialog(getShell(), "Error", "Exception creating new launch configuration.", ce);
 			clearLaunchConfiguration();
 			return;			
		}		
	}
	
	/**
	 * Notification the 'delete' button has been pressed
	 */
	protected void handleDeletePressed() {
		IStructuredSelection selection = getTreeViewerSelection();
		// The 'Delete' button is disabled if the selection contains anything other than configurations (no types)
		ILaunchConfiguration firstSelectedConfig = (ILaunchConfiguration) selection.getFirstElement();
		ILaunchConfigurationType firstSelectedConfigType = null;
		try {
			firstSelectedConfigType = firstSelectedConfig.getType();
		} catch (CoreException ce) {
			DebugUIPlugin.log(ce);						
		}
		Iterator iterator = selection.iterator();
		while (iterator.hasNext()) {
			clearLaunchConfiguration();
			Object selectedElement = iterator.next();
			if (selectedElement instanceof ILaunchConfiguration) {
				try {
					((ILaunchConfiguration)selectedElement).delete();
				} catch (CoreException ce) {
					DebugUIPlugin.log(ce);			
				}
			}
		}
		
		// Reset selection to the config type of the first selected configuration
		if (firstSelectedConfigType != null) {
			IStructuredSelection newSelection = new StructuredSelection(firstSelectedConfigType);
			getTreeViewer().setSelection(newSelection);
		}
	}	
	
	/**
	 * Notification the 'copy' button has been pressed
	 */
	protected void handleCopyPressed() {
		Object selectedElement = getTreeViewerFirstSelectedElement();
		if (selectedElement instanceof ILaunchConfiguration) {
			ILaunchConfiguration selectedConfig = (ILaunchConfiguration) selectedElement;
			String newName = generateUniqueNameFrom(selectedConfig.getName());
			try {
				ILaunchConfigurationWorkingCopy newWorkingCopy = selectedConfig.copy(newName);
				setLaunchConfiguration(newWorkingCopy, false);
				doSave();
				getTreeViewer().setSelection(new StructuredSelection(fUnderlyingConfig));
			} catch (CoreException ce) {
				DebugUIPlugin.log(ce);			
			}			
		}
	}	
	
	/**
	 * Construct a new config name using the name of the given config as a starting point.
	 * The new name is guaranteed not to collide with any existing config name.
	 */
	protected String generateUniqueNameFrom(String startingName) {
		int index = 1;
		String baseName = startingName;
		int underscoreIndex = baseName.lastIndexOf('_');
		if (underscoreIndex > -1) {
			String trailer = baseName.substring(underscoreIndex + 1);
			try {
				index = Integer.parseInt(trailer);
				baseName = startingName.substring(0, underscoreIndex);
			} catch (NumberFormatException nfe) {
			}
		} 
		String newName = baseName;
		try {
			while (getLaunchManager().isExistingLaunchConfigurationName(newName)) {
				StringBuffer buffer = new StringBuffer(baseName);
				buffer.append('_');
				buffer.append(String.valueOf(index));
				index++;
				newName = buffer.toString();		
			}		
		} catch (CoreException e) {
			DebugUIPlugin.log(e);
		}
		return newName;
	}
	
	/**
	 * Notification that the 'save' button has been pressed
	 */
	protected void handleSavePressed() {
		try {
			// trim name
			Text widget = getNameTextWidget();
			widget.setText(widget.getText().trim());
			doSave();
		} catch (CoreException e) {
			DebugUIPlugin.errorDialog(getShell(), "Launch Configuration Error", "Exception occurred while saving launch configuration.", e);
			return;
		}
		
		// Do the UI-related things required after a save
		updateButtons();
	}
	
	/**
	 * Notification that a tab has been selected
	 * 
	 * Disallow tab changing when the current tab is invalid.
	 * Update the config from the tab being left, and refresh
	 * the tab being entered.
	 */
	protected void handleTabSelected() {
		ILaunchConfigurationTab[] tabs = getTabs();
		if (fCurrentTabIndex == getTabFolder().getSelectionIndex() || tabs == null || tabs.length == 0 || fCurrentTabIndex > (tabs.length - 1)) {
			return;
		}
		if (fCurrentTabIndex != -1) {
			ILaunchConfigurationTab tab = tabs[fCurrentTabIndex];
			if (tab.okToLeave()) {
				ILaunchConfigurationWorkingCopy wc = getLaunchConfiguration();
				if (wc != null) {
					// apply changes when leaving a tab
					tab.performApply(getLaunchConfiguration());
					// update when entering a tab
					getActiveTab().initializeFrom(wc);
				}
			} else {
				getTabFolder().setSelection(fCurrentTabIndex);
				return;
			}
		}
		fCurrentTabIndex = getTabFolder().getSelectionIndex();
		refreshStatus();
	}	
	
	/**
	 * Iterate over the pages to update the working copy
	 */
	protected void updateWorkingCopyFromPages() {
		ILaunchConfigurationWorkingCopy workingCopy = getLaunchConfiguration();
		if (getTabGroup() != null) {
			getTabGroup().performApply(workingCopy);
		}
	}
	
	/**
	 * Do the save
	 */
	protected void doSave() throws CoreException {
		ILaunchConfigurationWorkingCopy workingCopy = getLaunchConfiguration();
		updateWorkingCopyFromPages();
		if (isWorkingCopyDirty()) {
			fUnderlyingConfig = workingCopy.doSave();
			setWorkingCopy(fUnderlyingConfig.getWorkingCopy());
			setLastSavedName(fUnderlyingConfig.getName()); 
		}
	}
	
	/**
	 * Notification the 'launch' button has been pressed.
	 * Save and launch.
	 */
	protected void handleLaunchPressed() {
		int result = CANCEL;
		try {
			doSave();
			result = doLaunch(getLaunchConfiguration());
		} catch (CoreException e) {
			DebugUIPlugin.errorDialog(getShell(), "Launch Configuration Error", "Exception occurred while launching configuration. See log for more information.", e);
			return;
		}
		if (result == OK) {
			if (fUnderlyingConfig != null) {
				try {
					getPreferenceStore().setValue(IDebugPreferenceConstants.PREF_LAST_LAUNCH_CONFIGURATION_SELECTION, fUnderlyingConfig.getMemento());
				} catch (CoreException e) {
					DebugUIPlugin.log(e);
				}
			}
			close();
		} else {
			getShell().setFocus();
		}
	}
	
	/**
	 * Autosave the working copy if necessary, then launch the underlying configuration.
	 * 
	 * @return one of CANCEL or OK
	 */
	protected int doLaunch(ILaunchConfiguration config) throws CoreException {
		
		if (!DebugUIPlugin.saveAndBuild()) {
			return CANCEL;
		}
		
		// If the configuration is a working copy and is dirty or doesn't yet exist, autosave it
		if (config instanceof ILaunchConfigurationWorkingCopy) {
			ILaunchConfigurationWorkingCopy workingCopy = (ILaunchConfigurationWorkingCopy) config;
			if (isWorkingCopyDirty() || !workingCopy.exists()) {
				// save the config
				fUnderlyingConfig = workingCopy.doSave();
			} 
		}
		

		ILaunch launch = launchWithProgress();
		
		// notify pages
		if (launch != null) {
			ILaunchConfigurationTabGroup group = getTabGroup();
			boolean disposeTabs = false;
			if (group == null) {
				// when doing a single click launch, tabs
				// may not exist - create and then dispose
				// so we can notify them of a launch
				disposeTabs = true;
				group = createGroup(config.getType());
			}
			group.launched(launch);
			if (disposeTabs) {
				group.dispose();
			}
		}
		
		return OK;
	}
	
	/**
	 * @return the resulting launch, or <code>null</code> if cancelled.
	 * @exception CoreException if an exception occurrs launching
	 */
	private ILaunch launchWithProgress() throws CoreException {
		final ILaunch[] launchResult = new ILaunch[1];
		// Do the launch
		IRunnableWithProgress runnable = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException {
				try {
					launchResult[0] = fUnderlyingConfig.launch(getMode(), monitor);
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				}
			}
		};
		try {
			run(true, true, runnable);
		} catch (InterruptedException e) {
			return null;
		} catch (InvocationTargetException e) {
			Throwable t = e.getTargetException();
			if (t instanceof CoreException) {
				throw (CoreException)t;
			} else {
				IStatus status = new Status(IStatus.ERROR, IDebugUIConstants.PLUGIN_ID, DebugException.INTERNAL_ERROR, "Exception occurred while launching.", t);
				throw new CoreException(status);
			}
		}
		
		
		return launchResult[0];		
	}
	
	protected IPreferenceStore getPreferenceStore() {
		return DebugUIPlugin.getDefault().getPreferenceStore();
	}

	/***************************************************************************************
	 * 
	 * ProgressMonitor & IRunnableContext related methods
	 * 
	 ***************************************************************************************/

	/**
	 * @see IRunnableContext#run(boolean, boolean, IRunnableWithProgress)
	 */
	public void run(boolean fork, boolean cancelable, IRunnableWithProgress runnable) throws InvocationTargetException, InterruptedException {
		if (isVisible()) {
			// The operation can only be canceled if it is executed in a separate thread.
			// Otherwise the UI is blocked anyway.
			Object state = aboutToStart(fork && cancelable);
			fActiveRunningOperations++;
			try {
				ModalContext.run(runnable, fork, fProgressMonitorPart, getShell().getDisplay());
			} finally {
				fActiveRunningOperations--;
				stopped(state);
			}
		} else {
			ProgressMonitorDialog dialog = new ProgressMonitorDialog(DebugUIPlugin.getShell());
			dialog.run(fork, cancelable, runnable);
		}
	}
	
	/**
	 * About to start a long running operation tiggered through
	 * the dialog. Shows the progress monitor and disables the dialog's
	 * buttons and controls.
	 *
	 * @param enableCancelButton <code>true</code> if the Cancel button should
	 *   be enabled, and <code>false</code> if it should be disabled
	 * @return the saved UI state
	 */
	private Object aboutToStart(boolean enableCancelButton) {
		Map savedState = null;
		if (getShell() != null) {
			// Save focus control
			Control focusControl = getShell().getDisplay().getFocusControl();
			if (focusControl != null && focusControl.getShell() != getShell())
				focusControl = null;
			
			Button cancelButton= getButton(IDialogConstants.CANCEL_ID);
			cancelButton.removeSelectionListener(fCancelListener);
			
			// Set the busy cursor to all shells.
			Display d = getShell().getDisplay();
			waitCursor = new Cursor(d, SWT.CURSOR_WAIT);
			setDisplayCursor(waitCursor);
					
			// Set the arrow cursor to the cancel component.
			arrowCursor= new Cursor(d, SWT.CURSOR_ARROW);
			cancelButton.setCursor(arrowCursor);
	
			// Deactivate shell
			savedState = saveUIState(enableCancelButton);
			if (focusControl != null)
				savedState.put(FOCUS_CONTROL, focusControl);
				
			// Attach the progress monitor part to the cancel button
			getProgressMonitorPart().attachToCancelComponent(cancelButton);
			getProgressMonitorPart().setVisible(true);
		}
		return savedState;
	}

	/**
	 * A long running operation triggered through the dialog
	 * was stopped either by user input or by normal end.
	 * Hides the progress monitor and restores the enable state
	 * dialog's buttons and controls.
	 *
	 * @param savedState the saved UI state as returned by <code>aboutToStart</code>
	 * @see #aboutToStart
	 */
	private void stopped(Object savedState) {
		if (getShell() != null) {
			getProgressMonitorPart().setVisible(false);
			Button cancelButton= getButton(IDialogConstants.CANCEL_ID);
			getProgressMonitorPart().removeFromCancelComponent(getButton(IDialogConstants.CANCEL_ID));
			Map state = (Map)savedState;
			restoreUIState(state);
			cancelButton.addSelectionListener(fCancelListener);
	
			setDisplayCursor(null);	
			cancelButton.setCursor(null);
			waitCursor.dispose();
			waitCursor = null;
			arrowCursor.dispose();
			arrowCursor = null;
			Control focusControl = (Control)state.get(FOCUS_CONTROL);
			if (focusControl != null)
				focusControl.setFocus();
		}
	}

	/**
	 * Captures and returns the enabled/disabled state of the wizard dialog's
	 * buttons and the tree of controls for the currently showing page. All
	 * these controls are disabled in the process, with the possible excepton of
	 * the Cancel button.
	 *
	 * @param keepCancelEnabled <code>true</code> if the Cancel button should
	 *   remain enabled, and <code>false</code> if it should be disabled
	 * @return a map containing the saved state suitable for restoring later
	 *   with <code>restoreUIState</code>
	 * @see #restoreUIState
	 */
	private Map saveUIState(boolean keepCancelEnabled) {
		Map savedState= new HashMap(10);
		saveEnableStateAndSet(getNewButton(), savedState, "new", false);//$NON-NLS-1$
		saveEnableStateAndSet(getDeleteButton(), savedState, "delete", false);//$NON-NLS-1$
		saveEnableStateAndSet(getCopyButton(), savedState, "copy", false);//$NON-NLS-1$
		saveEnableStateAndSet(getSaveButton(), savedState, "save", false);//$NON-NLS-1$
		saveEnableStateAndSet(getButton(IDialogConstants.CANCEL_ID), savedState, "cancel", keepCancelEnabled);//$NON-NLS-1$
		saveEnableStateAndSet(getButton(ID_LAUNCH_BUTTON), savedState, "launch", false);//$NON-NLS-1$
		TabItem selectedTab = getTabFolder().getItem(getTabFolder().getSelectionIndex());
		savedState.put("tab", ControlEnableState.disable(selectedTab.getControl()));//$NON-NLS-1$
		return savedState;
	}

	/**
	 * Saves the enabled/disabled state of the given control in the
	 * given map, which must be modifiable.
	 *
	 * @param w the control, or <code>null</code> if none
	 * @param h the map (key type: <code>String</code>, element type:
	 *   <code>Boolean</code>)
	 * @param key the key
	 * @param enabled <code>true</code> to enable the control, 
	 *   and <code>false</code> to disable it
	 * @see #restoreEnableStateAndSet
	 */
	private void saveEnableStateAndSet(Control w, Map h, String key, boolean enabled) {
		if (w != null) {
			h.put(key, new Boolean(w.isEnabled()));
			w.setEnabled(enabled);
		}
	}

	/**
	 * Restores the enabled/disabled state of the wizard dialog's
	 * buttons and the tree of controls for the currently showing page.
	 *
	 * @param state a map containing the saved state as returned by 
	 *   <code>saveUIState</code>
	 * @see #saveUIState
	 */
	private void restoreUIState(Map state) {
		restoreEnableState(getNewButton(), state, "new");//$NON-NLS-1$
		restoreEnableState(getDeleteButton(), state, "delete");//$NON-NLS-1$
		restoreEnableState(getCopyButton(), state, "copy");//$NON-NLS-1$
		restoreEnableState(getSaveButton(), state, "save");//$NON-NLS-1$
		restoreEnableState(getButton(IDialogConstants.CANCEL_ID), state, "cancel");//$NON-NLS-1$
		restoreEnableState(getButton(ID_LAUNCH_BUTTON), state, "launch");//$NON-NLS-1$
		ControlEnableState tabState = (ControlEnableState) state.get("tab");//$NON-NLS-1$
		tabState.restore();
	}

	/**
	 * Restores the enabled/disabled state of the given control.
	 *
	 * @param w the control
	 * @param h the map (key type: <code>String</code>, element type:
	 *   <code>Boolean</code>)
	 * @param key the key
	 * @see #saveEnableStateAndSet
	 */
	private void restoreEnableState(Control w, Map h, String key) {
		if (w != null) {
			Boolean b = (Boolean) h.get(key);
			if (b != null)
				w.setEnabled(b.booleanValue());
		}
	}

	/**
	 * Sets the given cursor for all shells currently active
	 * for this window's display.
	 *
	 * @param c the cursor
	 */
	private void setDisplayCursor(Cursor c) {
		Shell[] shells = getShell().getDisplay().getShells();
		for (int i = 0; i < shells.length; i++)
			shells[i].setCursor(c);
	}
	
	/**
	 * @see Dialog#cancelPressed()
	 */
	protected void cancelPressed() {
		if (fActiveRunningOperations <= 0) {
			super.cancelPressed();
		} else {
			getButton(IDialogConstants.CANCEL_ID).setEnabled(false);
		}
	}

	/**
	 * Checks whether it is alright to close this dialog
	 * and performed standard cancel processing. If there is a
	 * long running operation in progress, this method posts an
	 * alert message saying that the dialog cannot be closed.
	 * 
	 * @return <code>true</code> if it is alright to close this dialog, and
	 *  <code>false</code> if it is not
	 */
	private boolean okToClose() {
		if (fActiveRunningOperations > 0) {
			synchronized (this) {
				fWindowClosingDialog = createDialogClosingDialog();
			}	
			fWindowClosingDialog.open();
			synchronized (this) {
				fWindowClosingDialog = null;
			}
			return false;
		}
		
		return true;
	}

	/**
	 * Creates and return a new wizard closing dialog without opening it.
	 */ 
	private MessageDialog createDialogClosingDialog() {
		MessageDialog result= new MessageDialog(
			getShell(),
			JFaceResources.getString("WizardClosingDialog.title"),//$NON-NLS-1$
			null,
			JFaceResources.getString("WizardClosingDialog.message"),//$NON-NLS-1$
			MessageDialog.QUESTION,
			new String[] {IDialogConstants.OK_LABEL},
			0 ); 
		return result;
	}
	
	/**
	 * @see ILaunchConfigurationDialog#canLaunch()
	 */
	public boolean canLaunch() {
		try {
			verify();
		} catch (CoreException e) {
			return false;
		}
		
		ILaunchConfigurationTab[] tabs = getTabs();
		if (tabs == null) {
			return false;
		}
		for (int i = 0; i < tabs.length; i++) {
			if (!tabs[i].isValid()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @see ILaunchConfigurationDialog#getLaunchConfiguration()
	 */
	public ILaunchConfigurationWorkingCopy getLaunchConfiguration() {
		return fWorkingCopy;
	}

	/**
	 * @see ILaunchConfigurationDialog#updateButtons()
	 */
	public void updateButtons() {
		if (isInitializingTabs()) {
			return;
		}
		
		// new, copy, delete buttons
		IStructuredSelection sel = (IStructuredSelection)getTreeViewer().getSelection();
		
 		getNewButton().setEnabled(sel.size() == 1);
		getCopyButton().setEnabled(sel.size() == 1 && sel.getFirstElement() instanceof ILaunchConfiguration);
		
		if (sel.isEmpty()) {
			getDeleteButton().setEnabled(false); 		
		} else {
			Iterator iter = sel.iterator();
			boolean enable = true;
			while (iter.hasNext()) {
				if (iter.next() instanceof ILaunchConfigurationType) {
					enable = false;
				}
			}
			getDeleteButton().setEnabled(enable);
		}
		

		if (sel.isEmpty()) {
			getSaveButton().setEnabled(false);
			getButton(ID_LAUNCH_BUTTON).setEnabled(false);
		} else {
			// save and launch buttons
			ILaunchConfigurationTab tab = getActiveTab();
			boolean verified = false;
			try {
				verify();
				verified = true;
			} catch (CoreException e) {
			}
			if (tab != null) {
				getSaveButton().setEnabled(verified && tab.isValid());
				getButton(ID_LAUNCH_BUTTON).setEnabled(canLaunch());		
			}
		}
	}
	
	/**
	 * Returns the currently active tab
	 * 
	 * @return launch configuration tab
	 */
	protected ILaunchConfigurationTab getActiveTab() {
		TabFolder folder = getTabFolder();
		ILaunchConfigurationTab[] tabs = getTabs();
		if (folder != null && tabs != null) {
			int pageIndex = folder.getSelectionIndex();
			if (pageIndex >= 0) {		
				return tabs[pageIndex];		
			}
		}
		return null;
	}

	/**
	 * @see ILaunchConfigurationDialog#updateMessage()
	 */
	public void updateMessage() {
		if (isInitializingTabs()) {
			return;
		}
		
		if (getLaunchConfiguration() == null) {
			setErrorMessage(null);
			setMessage("Select a type of configuration to create, and press 'new'.");
			return;
		}
		
		try {
			verify();
		} catch (CoreException e) {
			setErrorMessage(e.getMessage());
			return;
		}
		
		ILaunchConfigurationTab page = getActiveTab();
		if (page == null) {
			setMessage(null);
			setErrorMessage(null);
			return;
		}
		
		
		setErrorMessage(page.getErrorMessage());
		setMessage(page.getMessage());

	}

	/**
	 * Returns the launch configuration edit area control.
	 * 
	 * @return control
	 */
	protected Control getEditArea() {
		return fEditArea;
	}

	/**
	 * Sets the launch configuration edit area control.
	 * 
	 * @param editArea control
	 */
	private void setEditArea(Control editArea) {
		fEditArea = editArea;
	}

	/**
	 * Returns the type that tabs are currently displayed
	 * for, or <code>null</code> if none.
	 * 
	 * @return launch configuration type or <code>null</code>
	 */
	protected ILaunchConfigurationType getTabType() {
		return fTabType;
	}

	/**
	 * Sets the type that tabs are currently displayed
	 * for, or <code>null</code> if none.
	 * 
	 * @param tabType launch configuration type
	 */
	private void setTabType(ILaunchConfigurationType tabType) {
		fTabType = tabType;
	}

	protected Object getSelectedTreeObject() {
		return fSelectedTreeObject;
	}
	
	protected void setSelectedTreeObject(Object obj) {
		fSelectedTreeObject = obj;
	}	
	
	/**
	 * @see ILaunchConfigurationDialog#setName(String)
	 */
	public void setName(String name) {
		if (isVisible()) {
			if (name == null) {
				name = "";
			}
			fNameText.setText(name.trim());
			refreshStatus();
		}
	}
	
	/**
	 * @see ILaunchConfigurationDialog#generateName(String)
	 */
	public String generateName(String name) {
		if (name == null) {
			name = "";
		}
		return generateUniqueNameFrom(name);
	}

	/**
	 * @see IDoubleClickListener#doubleClick(DoubleClickEvent)
	 */
	public void doubleClick(DoubleClickEvent event) {		
		ISelection selection = event.getSelection();		
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection structuredSelection = (IStructuredSelection) selection;
			Object firstSelected = structuredSelection.getFirstElement();
			if (firstSelected instanceof ILaunchConfigurationType) {
				handleNewPressed();
			} else if (firstSelected instanceof ILaunchConfiguration) {
				if (canLaunch()) {
					handleLaunchPressed();
				}				
			}
		}		
	}
	
	/**
	 * Sets whether this dialog is initializing pages
	 * and should not bother to refresh status (butttons
	 * and message).
	 */
	private void setInitializingTabs(boolean init) {
		fInitializingTabs = init;
	}
	
	/**
	 *Returns whether this dialog is initializing pages
	 * and should not bother to refresh status (butttons
	 * and message).
	 */
	protected boolean isInitializingTabs() {
		return fInitializingTabs;
	}	

	/**
	 * Returns whether the dialog should attempt single
	 * click launching when opened.
	 */
	protected boolean isAttemptSingleClickLaunch() {
		return fTrySingleClick && getPreferenceStore().getBoolean(IDebugUIConstants.PREF_SINGLE_CLICK_LAUNCHING);
	}
	
	/**
	 * Enables/disabled single click launching when opened.
	 */
	public void setSingleClickLaunch(boolean enabled) {
		fTrySingleClick = enabled;
	}
	
	/**
	 * Returns the initial launch configuration type, or <code>null</code> if none has been set.
	 */
	protected ILaunchConfigurationType getInitialConfigType() {
		return fInitialConfigType;
	}
	
	/**
	 * Sets the initial launch configuration type to be used when this dialog is opened.
	 */
	public void setInitialConfigType(ILaunchConfigurationType configType) {
		fInitialConfigType = configType;
	}
}