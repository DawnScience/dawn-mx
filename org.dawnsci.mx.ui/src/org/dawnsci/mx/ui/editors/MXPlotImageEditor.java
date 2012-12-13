/*
 * Copyright (c) 2012 European Synchrotron Radiation Facility,
 *                    Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */ 

package org.dawnsci.mx.ui.editors;

import javax.vecmath.Vector3d;

import org.dawb.common.services.ILoaderService;
import org.dawb.common.services.ServiceManager;
import org.dawb.common.ui.editors.IEditorExtension;
import org.dawb.common.ui.menu.MenuAction;
import org.dawb.common.ui.plot.AbstractPlottingSystem;
import org.dawb.common.ui.plot.AbstractPlottingSystem.ColorOption;
import org.dawb.common.ui.plot.IPlotActionSystem;
import org.dawb.common.ui.plot.PlotType;
import org.dawb.common.ui.plot.PlottingFactory;
import org.dawb.common.ui.plot.tool.IToolPage.ToolPageRole;
import org.dawb.common.ui.plot.tool.IToolPageSystem;
import org.dawb.common.ui.plot.trace.IImageTrace;
import org.dawb.common.ui.plot.trace.ITrace;
import org.dawb.common.ui.util.EclipseUtils;
import org.dawb.common.ui.util.GridUtils;
import org.dawb.common.ui.views.HeaderTablePage;
import org.dawb.workbench.plotting.tools.diffraction.DiffractionImageAugmenter;
import org.dawnsci.mx.ui.Activator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IReusableEditor;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.part.EditorPart;
import org.eclipse.ui.part.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.diffraction.DetectorProperties;
import uk.ac.diamond.scisoft.analysis.diffraction.DiffractionCrystalEnvironment;
import uk.ac.diamond.scisoft.analysis.io.DiffractionMetaDataAdapter;
import uk.ac.diamond.scisoft.analysis.io.IDiffractionMetadata;
import uk.ac.diamond.scisoft.analysis.io.IMetaData;
import uk.ac.diamond.scisoft.analysis.rcp.AnalysisRCPActivator;
import uk.ac.diamond.scisoft.analysis.rcp.preference.PreferenceConstants;

/**
 * An editor with an image and a reduced set of tools relevant for MX.
 * 
 */
public class MXPlotImageEditor extends EditorPart implements IReusableEditor, IEditorExtension {
	
	public static final String ID = "uk.ac.diamond.scisoft.mx.rcp.editors.mxplotimageeditor";

	private static Logger logger = LoggerFactory.getLogger(MXPlotImageEditor.class);
	
	protected Composite                   tools;

	// Colours
	protected PaletteData paletteData;
	protected int histoMin = 0;
	protected int histoMax = 255;
	protected IImageTrace imageTrace;
	
	private AbstractPlottingSystem plottingSystem;
	protected DiffractionImageAugmenter augmenter;

	private DetectorProperties detectorProperties;
	private DiffractionCrystalEnvironment diffractionCrystalEnvironment;

	public MXPlotImageEditor() {
		try {
			plottingSystem = PlottingFactory.createPlottingSystem();
	        plottingSystem.setColorOption(ColorOption.NONE);
		} catch (Exception ne) {
			logger.error("Cannot locate any plotting systems!", ne);
		}
		augmenter = new DiffractionImageAugmenter(plottingSystem);
 	}

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		
		setSite(site);
		setInput(input);
		//setPartName("MX image"); // input.getName()
	}
	
	
	@Override
	public void setInput(final IEditorInput input) {
		super.setInput(input);
		setPartName("MX image"); 
		//createPlot();
	}


	@Override
	public boolean isDirty() {
		return false;
	}
	

	public void setToolbarsVisible(boolean isVisible) {
		GridUtils.setVisible(tools, isVisible);
		tools.getParent().layout(new Control[]{tools});
	}

	@Override
	public void createPartControl(final Composite parent) {
		
		final Composite  main       = new Composite(parent, SWT.NONE);
		final GridLayout gridLayout = new GridLayout(1, false);
		gridLayout.verticalSpacing = 0;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		gridLayout.horizontalSpacing = 0;
		main.setLayout(gridLayout);
		
		this.tools = new Composite(main, SWT.RIGHT);
		tools.setLayout(new GridLayout(2, false));
		GridUtils.removeMargins(tools);
		tools.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));

		// We use a local toolbar to make it clear to the user the tools
		// that they can use, also because the toolbar actions are 
		// hard coded.

		ToolBarManager toolMan = new ToolBarManager(SWT.FLAT|SWT.RIGHT|SWT.WRAP);
		final ToolBar  toolBar = toolMan.createControl(tools);
		toolBar.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

		//createCustomToolbarActionsRight(toolMan);
		//
		ToolBarManager rightMan = new ToolBarManager(SWT.FLAT|SWT.RIGHT|SWT.WRAP);
		final ToolBar  rightBar = rightMan.createControl(tools);
		rightBar.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

		final MenuManager    menuMan = new MenuManager();
		//final IActionBars bars = this.getEditorSite().getActionBars();
		//ActionBarWrapper wrapper = new ActionBarWrapper(toolMan,menuMan,null,(IActionBars2)bars);
		//ActionBarWrapper wrapper = new ActionBarWrapper(toolMan,null,null,null);
		
		// NOTE use name of input. This means that although two files of the same
		// name could be opened, the editor name is clearly visible in the GUI and
		// is usually short.
		final String plotName = this.getEditorInput().getName();

		final Composite plot = new Composite(main, SWT.NONE);
		plot.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		plot.setLayout(new FillLayout());

        plottingSystem.createPlotPart(plot, plotName, null, PlotType.IMAGE, this);
		createPlot();
        IPlotActionSystem actionsys = plottingSystem.getPlotActionSystem();
        actionsys.fillZoomActions(toolMan);
        actionsys.fillRegionActions(toolMan);
        actionsys.fillToolActions(toolMan, ToolPageRole.ROLE_2D);
        
	    MenuAction dropdown = new MenuAction("Resolution rings");
	    dropdown.setImageDescriptor(Activator.getImageDescriptor("/icons/resolution_rings.png"));

	    augmenter.addActions(dropdown);
	    toolMan.add(dropdown);
	    
	    
	    Action menuAction = new Action("", Activator.getImageDescriptor("/icons/DropDown.png")) {
	    	@Override
	    	public void run() {
	    		final Menu   mbar = menuMan.createContextMenu(toolBar);
	    		mbar.setVisible(true);
	    	}
	    };
	    rightMan.add(menuAction);

		menuMan.add(new Action("Diffraction Viewer Preferences", null) {
	    	@Override
	    	public void run() {
				PreferenceDialog pref = PreferencesUtil.createPreferenceDialogOn(PlatformUI.getWorkbench().
						getActiveWorkbenchWindow().getShell(), 
						"uk.ac.diamond.scisoft.analysis.rcp.diffractionViewerPreferencePage", null, null);
				if (pref != null) pref.open();
	    	}
		});

	    
	    
		if (toolMan!=null)  toolMan.update(true);
		if (rightMan!=null) rightMan.update(true);
		
		getEditorSite().setSelectionProvider(plottingSystem.getSelectionProvider());
 	}

	private class MXMetadataAdapter extends DiffractionMetaDataAdapter {
		private static final long serialVersionUID = DiffractionMetaDataAdapter.serialVersionUID;
		private final DetectorProperties props;
		private final DiffractionCrystalEnvironment env;

		public MXMetadataAdapter(DetectorProperties props, DiffractionCrystalEnvironment env) {
			this.props = props;
			this.env = env;
		}

		@Override
		public DetectorProperties getDetector2DProperties() {
			return props;
		}

		@Override
		public DiffractionCrystalEnvironment getDiffractionCrystalEnvironment() {
			return env;
		}

		@Override
		public DetectorProperties getOriginalDetector2DProperties() {
			return detectorProperties;
		}

		@Override
		public DiffractionCrystalEnvironment getOriginalDiffractionCrystalEnvironment() {
			return diffractionCrystalEnvironment;
		}

		@Override
		public MXMetadataAdapter clone() {
			return new MXMetadataAdapter(props.clone(), env.clone());
		}
	}

	private void createPlot() {
		
		final Job job = new Job("Read image data") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				
				final String filePath = EclipseUtils.getFilePath(getEditorInput());
				AbstractDataset set;
				try {
					final ILoaderService service = (ILoaderService)ServiceManager.getService(ILoaderService.class);
					set = service.getDataset(filePath);
				} catch (Throwable e) {
					logger.error("Cannot load file "+filePath, e);
					return Status.CANCEL_STATUS;
				}

				if (filePath == null)
					logger.error("Cannot get either file path or dataset");
				else if (set == null) {
					logger.error("Cannot get dataset for file "+filePath);
				}
				else {
					set.setName(""); // Stack trace if null - stupid.
					ITrace trace = plottingSystem.updatePlot2D(set, null, monitor);
					try {
						IMetaData localMetaData = set.getMetadata();
						// Get image size in x and y directions
						int[] shape = set.getShape();
						int heightInPixels = shape[0];
						int widthInPixels = shape[1];

						if (localMetaData instanceof IDiffractionMetadata) {
							IDiffractionMetadata localDiffractionMetaData = (IDiffractionMetadata)localMetaData;
							augmenter.setDiffractionMetadata(localDiffractionMetaData);
						} else {
							// Set a few default values
							double pixelSizeX = 0.1024;
							double pixelSizeY = 0.1024;
							double distance = 200.00;

							// Create the detector origin vector based on the above
							double[] detectorOrigin = { (widthInPixels - widthInPixels/2d) * pixelSizeX, (heightInPixels - heightInPixels/2d) * pixelSizeY, distance };

							detectorProperties = new DetectorProperties(new Vector3d(detectorOrigin), heightInPixels, widthInPixels, 
									pixelSizeX, pixelSizeY, null);

							// Set a few default values
							double lambda = 0.9;
							double startOmega = 0.0;
							double rangeOmega = 1.0;
							double exposureTime = 1.0;

							diffractionCrystalEnvironment = new DiffractionCrystalEnvironment(lambda, startOmega, rangeOmega, exposureTime);

							localMetaData = new MXMetadataAdapter(detectorProperties.clone(), diffractionCrystalEnvironment.clone());

							augmenter.setDiffractionMetadata((IDiffractionMetadata) localMetaData);
							set.setMetadata(localMetaData);
						}
						augmenter.setImageCentre(widthInPixels/2.,heightInPixels/2.);
					} catch (Exception e) {
						logger.error("Could not create diffraction experiment objects");
					}
				}
				return Status.OK_STATUS;
			}
		};
		job.setUser(false);
		job.setPriority(Job.BUILD);
		job.schedule();
	}

	/**
	 * Override to provide extra content.
	 * @param toolMan
	 */
	protected void createCustomToolbarActionsRight(final ToolBarManager toolMan) {

		toolMan.add(new Separator(getClass().getName()+"Separator1"));

		final Action tableColumns = new Action("Open editor preferences.", IAction.AS_PUSH_BUTTON) {
			@Override
			public void run() {
				PreferenceDialog pref = PreferencesUtil.createPreferenceDialogOn(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "uk.ac.diamond.scisoft.mxv1.rcp.mxv1PreferencePage", null, null);
				if (pref != null) pref.open();
			}
		};
		tableColumns.setChecked(false);
		tableColumns.setImageDescriptor(Activator.getImageDescriptor("icons/application_view_columns.png"));

		toolMan.add(tableColumns);
		
	}

	@Override
	public void setFocus() {
		if (plottingSystem!=null && plottingSystem.getPlotComposite()!=null) {
			plottingSystem.setFocus();
		}
	}

	@Override
	public void doSave(IProgressMonitor monitor) {

	}

	@Override
	public void doSaveAs() {

	}

	@Override
	public boolean isSaveAsAllowed() {
		return false;
	}

    @Override
    public void dispose() {
    	augmenter.dispose();
     	if (plottingSystem!=null) plottingSystem.dispose();
     	super.dispose();
    }

    @Override
    public Object getAdapter(@SuppressWarnings("rawtypes") final Class clazz) {
		
		if (clazz == Page.class) {
			return new HeaderTablePage(EclipseUtils.getFilePath(getEditorInput()));
		} else if (clazz == IToolPageSystem.class) {
			return plottingSystem;
		}
		
		return super.getAdapter(clazz);
	}
    
    public AbstractPlottingSystem getPlottingSystem() {
    	return plottingSystem;
    }

	@Override
	public boolean isApplicable(final String filePath, final String extension, final String perspectiveId) {
		IPreferenceStore preferenceStore = AnalysisRCPActivator.getDefault().getPreferenceStore();
		boolean mxImageGlobal = false;
		if (preferenceStore.isDefault(PreferenceConstants.DIFFRACTION_VIEWER_MX_IMAGE_GLOBAL))
			mxImageGlobal = preferenceStore.getDefaultBoolean(PreferenceConstants.DIFFRACTION_VIEWER_MX_IMAGE_GLOBAL);
		else
			mxImageGlobal = preferenceStore.getBoolean(PreferenceConstants.DIFFRACTION_VIEWER_MX_IMAGE_GLOBAL);

		if (mxImageGlobal) 
			return true;
		
		final String MXLIVE_ID = "uk.ac.diamond.sda.mxlive.mxliveperspective";
		final String DIVA_ID = "uk.ac.diamond.scisoft.diffractionviewerperspective";
		final String MX_ID = "uk.ac.diamond.scisoft.mx.rcp.mxperspective";
		if (MX_ID.equalsIgnoreCase(perspectiveId) || MXLIVE_ID.equalsIgnoreCase(perspectiveId) 
				|| DIVA_ID.equalsIgnoreCase(perspectiveId))
			return true;

		return false;
	}

}
