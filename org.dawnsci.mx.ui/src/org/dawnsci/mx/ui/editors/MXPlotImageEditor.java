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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.StringTokenizer;

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
import org.dawb.common.ui.plot.region.IRegion;
import org.dawb.common.ui.plot.region.IRegion.RegionType;
import org.dawb.common.ui.plot.region.RegionUtils;
import org.dawb.common.ui.plot.tool.IToolPage.ToolPageRole;
import org.dawb.common.ui.plot.tool.IToolPageSystem;
import org.dawb.common.ui.plot.trace.IImageTrace;
import org.dawb.common.ui.plot.trace.ITrace;
import org.dawb.common.ui.util.EclipseUtils;
import org.dawb.common.ui.util.GridUtils;
import org.dawb.common.ui.views.HeaderTablePage;
import org.dawb.workbench.plotting.system.swtxy.selection.AbstractSelectionRegion;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.draw2d.ColorConstants;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
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
import uk.ac.diamond.scisoft.analysis.diffraction.DetectorPropertyEvent;
import uk.ac.diamond.scisoft.analysis.diffraction.DiffractionCrystalEnvironment;
import uk.ac.diamond.scisoft.analysis.diffraction.IDetectorPropertyListener;
import uk.ac.diamond.scisoft.analysis.diffraction.Resolution;
import uk.ac.diamond.scisoft.analysis.io.IDiffractionMetadata;
import uk.ac.diamond.scisoft.analysis.io.IMetaData;
import uk.ac.diamond.scisoft.analysis.rcp.AnalysisRCPActivator;
import uk.ac.diamond.scisoft.analysis.rcp.preference.PreferenceConstants;
import uk.ac.diamond.scisoft.analysis.roi.LinearROI;
import uk.ac.diamond.scisoft.analysis.roi.ResolutionRing;
import uk.ac.diamond.scisoft.analysis.roi.ResolutionRingList;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;

import org.dawnsci.mx.ui.Activator;

/**
 * An editor which combines a plot with a graph of data sets.
 * 
 * Currently this is for 1D analyses only so if the data does not contain 1D, this
 * editor will not show.
 * 
 */
public class MXPlotImageEditor extends EditorPart implements IReusableEditor, IEditorExtension, IDetectorPropertyListener {
	
	public static final String ID = "uk.ac.diamond.scisoft.mx.rcp.editors.mxplotimageeditor";

	private static Logger logger = LoggerFactory.getLogger(MXPlotImageEditor.class);
	
	// This view is a composite of two other views.
	protected AbstractPlottingSystem      plottingSystem;	
	protected Composite                   tools;

	// Colours
	protected PaletteData paletteData;
	protected int histoMin = 0;
	protected int histoMax = 255;
	protected IImageTrace imageTrace;
	
	protected final static double[] iceResolution = new double[] { 3.897, 3.669, 3.441, 2.671, 2.249, 2.072, 1.948,
		1.918, 1.883, 1.721 };// angstrom

	// data that is being plotted
	protected DetectorProperties detConfig;
	protected DiffractionCrystalEnvironment diffEnv;
	
	// Standard rings
	ResolutionRingList standardRingsList;
	ArrayList<IRegion> standardRingsRegionList;

	// Ice rings
	ResolutionRingList iceRingsList;
	ArrayList<IRegion> iceRingsRegionList;

	// Calibrant rings
	ResolutionRingList calibrantRingsList;
	ArrayList<IRegion> calibrantRingsRegionList;

	IRegion beamCentreRegion;

	Action standardRings, iceRings, calibrantRings, beamCentre;
	
	public MXPlotImageEditor() {
	
		try {
	        this.plottingSystem = PlottingFactory.createPlottingSystem();
	        plottingSystem.setColorOption(ColorOption.NONE);
		} catch (Exception ne) {
			logger.error("Cannot locate any plotting systems!", ne);
		}
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
		setPartName("MX image"); //input.getName()
		createPlot();
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
        IPlotActionSystem actionsys = plottingSystem.getPlotActionSystem();
        actionsys.fillZoomActions(toolMan);
        actionsys.fillRegionActions(toolMan);
        actionsys.fillToolActions(toolMan, ToolPageRole.ROLE_2D);
        
	    MenuAction dropdown = new MenuAction("Resolution rings");
	    dropdown.setImageDescriptor(Activator.getImageDescriptor("/icons/resolution_rings.png"));

	    standardRings = new Action("Standard rings", Activator.getImageDescriptor("/icons/standard_rings.png")) {
	    	@Override
	    	public void run() {
	    		drawStandardRings();
	    	}
		};
		standardRings.setChecked(false);
		iceRings = new Action("Ice rings", Activator.getImageDescriptor("/icons/ice_rings.png")) {
			@Override
			public void run() {
				drawIceRings();
			}
		};
		iceRings.setChecked(false);
		calibrantRings = new Action("Calibrant", Activator.getImageDescriptor("/icons/calibrant_rings.png")) {
			@Override
			public void run() {
				drawCalibrantRings();
			}
		};
		calibrantRings.setChecked(false);
		beamCentre = new Action("Beam centre", Activator.getImageDescriptor("/icons/beam_centre.png")) {
			@Override
			public void run() {
				drawBeamCentre();
			}
		};
		beamCentre.setChecked(false);
		
		dropdown.add(standardRings);
		dropdown.add(iceRings);
	    dropdown.add(calibrantRings);
	    dropdown.add(beamCentre);

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
		//createPlot();
		
		getEditorSite().setSelectionProvider(plottingSystem.getSelectionProvider());
 	}

	protected void removeRings(ArrayList<IRegion> regionList, ResolutionRingList resolutionRingList) {
		for (IRegion region : regionList) {
			plottingSystem.removeRegion(region);
		}
		regionList.clear();
		resolutionRingList.clear();
	}
	
	/*
	 * handle ring drawing, removal and clearing
	 */
	protected IRegion drawRing(int[] beamCentre, double innerRadius, double outerRadius, Color colour, Color labelColour, String nameStub, String labelText) {
		IRegion region;
		try {
			final String regionName = RegionUtils.getUniqueName(nameStub, plottingSystem);
			region = plottingSystem.createRegion(regionName, RegionType.RING);
		} catch (Exception e) {
			logger.error("Can't create region", e);
			return null;
		}
	    final SectorROI sroi = new SectorROI(innerRadius, outerRadius);
	    sroi.setPoint(beamCentre[0], beamCentre[1]);
		region.setROI(sroi);
		region.setRegionColor(colour);
		region.setAlpha(100);
		region.setUserRegion(false);
		region.setMobile(false);
		
		region.setLabel(labelText);
		((AbstractSelectionRegion)region).setShowLabel(true);
		((AbstractSelectionRegion)region).setForegroundColor(labelColour);
		
		region.setShowPosition(false);
		plottingSystem.addRegion(region);
		
		return region;
	}
	
	protected IRegion drawCrosshairs(double[] beamCentre, double length, Color colour, Color labelColour, String nameStub, String labelText) {
		IRegion region;
		try {
			final String regionName = RegionUtils.getUniqueName(nameStub, plottingSystem);
			region = plottingSystem.createRegion(regionName, RegionType.LINE);
		} catch (Exception e) {
			logger.error("Can't create region", e);
			return null;
		}

		final LinearROI lroi = new LinearROI(length, 0);
		double dbc[] = {(double)beamCentre[0], (double)beamCentre[1]};
		lroi.setMidPoint(dbc);
		lroi.setCrossHair(true);
		region.setROI(lroi);
		region.setRegionColor(colour);
		region.setAlpha(100);
		region.setUserRegion(false);
		region.setShowPosition(false);
		
		region.setLabel(labelText);
		((AbstractSelectionRegion)region).setShowLabel(true);
		
		plottingSystem.addRegion(region);
		region.setMobile(false); // NOTE: Must be done **AFTER** calling the addRegion method.

		return region;
	}
	
	protected IRegion drawResolutionRing(ResolutionRing ring, String name) {
		int[] beamCentre = detConfig.pixelCoords(detConfig.getBeamPosition());
		double radius = Resolution.circularResolutionRingRadius(detConfig, diffEnv, ring.getResolution());
		DecimalFormat df = new DecimalFormat("#.00");
		return drawRing(beamCentre, radius, radius+4.0, ring.getColour(), ring.getColour(), name, df.format(ring.getResolution())+"Å");
	}
	
	protected ArrayList<IRegion> drawResolutionRings(ResolutionRingList ringList, String typeName) {
		ArrayList<IRegion> regions = new ArrayList<IRegion>(); 
		for (int i = 0; i < ringList.size(); i++) {
			regions.add(drawResolutionRing(ringList.get(i), typeName+i));
		}
		return regions;
	}
	
	protected void drawStandardRings() {
		if (!standardRings.isChecked()) {
			if (standardRingsRegionList != null && standardRingsList != null)
				removeRings(standardRingsRegionList, standardRingsList); 
		}
		else if (diffEnv!= null && detConfig != null) {
			standardRingsList = new ResolutionRingList();
			Double numberEvenSpacedRings = 6.0;
			double lambda = diffEnv.getWavelength();
			Vector3d longestVector = detConfig.getLongestVector();
			double step = longestVector.length() / numberEvenSpacedRings; 
			double d, twoThetaSpacing;
			Vector3d toDetectorVector = new Vector3d();
			Vector3d beamVector = detConfig.getBeamPosition();
			for (int i = 0; i < numberEvenSpacedRings - 1; i++) {
				// increase the length of the vector by step.
				longestVector.normalize();
				longestVector.scale(step + (step * i));
	
				toDetectorVector.add(beamVector, longestVector);
				twoThetaSpacing = beamVector.angle(toDetectorVector);
				d = lambda / Math.sin(twoThetaSpacing);
				standardRingsList.add(new ResolutionRing(d, true, ColorConstants.yellow, false, true, true));
			}
			standardRingsRegionList = drawResolutionRings(standardRingsList, "standard");
		}
	}

	protected void drawIceRings() {
		if (!iceRings.isChecked()) {
			if (iceRingsRegionList!=null && iceRingsList!=null) {
				removeRings(iceRingsRegionList, iceRingsList);
			}
		}
		else {
			iceRingsList = new ResolutionRingList();
			
			for (double res : iceResolution) {
				iceRingsList.add(new ResolutionRing(res, true, ColorConstants.blue, true, false, false));
			}
			iceRingsRegionList = drawResolutionRings(iceRingsList, "ice");
		}
	}
	
	protected void drawBeamCentre() {
		if (!beamCentre.isChecked()) {
			if (beamCentreRegion != null)
				plottingSystem.removeRegion(beamCentreRegion);
		}
		else if (detConfig != null) {
			double[] beamCentrePC = detConfig.getBeamLocation();
			double length = (1 + Math.sqrt(detConfig.getPx() * detConfig.getPx() + detConfig.getPy() * detConfig.getPy()) * 0.01);
			DecimalFormat df = new DecimalFormat("#.##");
			String label = df.format(beamCentrePC[0]) + "px, " + df.format(beamCentrePC[1])+"px";
			beamCentreRegion = drawCrosshairs(beamCentrePC, length, ColorConstants.red, ColorConstants.black, "beam centre", label);
		}
		else {
			final AbstractDataset image = imageTrace.getData();
			double[] beamCentrePC = new double[]{image.getShape()[1]/2d, image.getShape()[0]/2d};
			DecimalFormat df = new DecimalFormat("#.##");
			String label = df.format(beamCentrePC[0]) + "px, " + df.format(beamCentrePC[1])+"px";
	    	beamCentreRegion = drawCrosshairs(beamCentrePC, image.getShape()[1]/100, ColorConstants.red, ColorConstants.black, "beam centre", label);
		}
	}

	protected void drawCalibrantRings() {
		// Remove rings if unchecked
		if (!calibrantRings.isChecked()) {
			if (calibrantRingsRegionList!=null && calibrantRingsList != null) {
				removeRings(calibrantRingsRegionList, calibrantRingsList);
			}
		}
		else {
			calibrantRingsList = new ResolutionRingList();

			IPreferenceStore preferenceStore = AnalysisRCPActivator.getDefault().getPreferenceStore();
			@SuppressWarnings("unused")
			String standardName;
			if (preferenceStore.isDefault(PreferenceConstants.DIFFRACTION_VIEWER_STANDARD_NAME))
				standardName = preferenceStore.getDefaultString(PreferenceConstants.DIFFRACTION_VIEWER_STANDARD_NAME);
			else
				standardName = preferenceStore.getString(PreferenceConstants.DIFFRACTION_VIEWER_STANDARD_NAME);

			String standardDistances;
			if (preferenceStore.isDefault(PreferenceConstants.DIFFRACTION_VIEWER_STANDARD_DISTANCES))
				standardDistances = preferenceStore
				.getDefaultString(PreferenceConstants.DIFFRACTION_VIEWER_STANDARD_DISTANCES);
			else
				standardDistances = preferenceStore.getString(PreferenceConstants.DIFFRACTION_VIEWER_STANDARD_DISTANCES);

			ArrayList<Double> dSpacing = new ArrayList<Double>();
			StringTokenizer st = new StringTokenizer(standardDistances, ",");
			while (st.hasMoreTokens()) {
				String temp = st.nextToken();
				dSpacing.add(Double.valueOf(temp));
			}
			for (double d : dSpacing) {
				calibrantRingsList.add(new ResolutionRing(d, true, ColorConstants.red, true, false, false));
			}
			calibrantRingsRegionList = drawResolutionRings(calibrantRingsList, "calibrant");
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

				set.setName(""); // Stack trace if null - stupid.
				ITrace trace = plottingSystem.updatePlot2D(set, null, monitor);
				
				// Set the palette to negative gray scale ("film negative")
				if (trace instanceof IImageTrace) {
					imageTrace = (IImageTrace) trace;
					paletteData = imageTrace.getPaletteData();
					paletteData.colors = new RGB[256];
					for (int i = 0; i < 256; i++) {
						paletteData.colors[i] = new RGB(255-i, 255-i, 255-i);
					}
				}
				
				try {
					IMetaData localMetaData = set.getMetadata();
					if (localMetaData instanceof IDiffractionMetadata) {
						IDiffractionMetadata localDiffractionMetaData = (IDiffractionMetadata)localMetaData;
						detConfig = localDiffractionMetaData.getDetector2DProperties();
						diffEnv = localDiffractionMetaData.getDiffractionCrystalEnvironment();
						
						// TODO Listen to properties changing
						detConfig.addDetectorPropertyListener(MXPlotImageEditor.this);
					}
				} catch (Exception e) {
					logger.error("Could not create diffraction experiment objects");
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
     	if (plottingSystem!=null) plottingSystem.dispose();
     	super.dispose();
     	
		// TODO remove this as a listener for detector properties.
        detConfig.removeDetectorPropertyListener(MXPlotImageEditor.this);
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
    	return this.plottingSystem;
    }

	@Override
	public boolean isApplicable(final String filePath, final String extension, final String perspectiveId) {
		IPreferenceStore preferenceStore = AnalysisRCPActivator.getDefault().getPreferenceStore();
		boolean mxImageGlobal = false;
		if (preferenceStore.isDefault(PreferenceConstants.DIFFRACTION_VIEWER_MX_IMAGE_GLOBAL))
			mxImageGlobal = preferenceStore.getDefaultBoolean(PreferenceConstants.DIFFRACTION_VIEWER_MX_IMAGE_GLOBAL);
		else
			mxImageGlobal = preferenceStore.getBoolean(PreferenceConstants.DIFFRACTION_VIEWER_MX_IMAGE_GLOBAL);

		if 	("mccd".equalsIgnoreCase(extension) || "img".equalsIgnoreCase(extension) || "cbf".equalsIgnoreCase(extension)) {
			if (mxImageGlobal) 
				return true;
		
			final String MXLIVE_ID = "uk.ac.diamond.sda.mxlive.mxliveperspective";
			final String DIVA_ID = "uk.ac.diamond.scisoft.diffractionviewerperspective";
			final String MX_ID = "uk.ac.diamond.scisoft.mx.rcp.mxperspective";
			if (MX_ID.equalsIgnoreCase(perspectiveId) || MXLIVE_ID.equalsIgnoreCase(perspectiveId) 
					|| DIVA_ID.equalsIgnoreCase(perspectiveId))
				return true;
		}
		return false;
	}

	@Override
	public void detectorPropertiesChanged(DetectorPropertyEvent evt) {
		// TODO Karl can we listen to changed properties ok?
		String property = evt.getPropertyName();
		if ("Beam Center".equals(property)) {
			drawBeamCentre();
		}
	}
}
