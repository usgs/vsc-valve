package gov.usgs.valve3;

import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.ColorCycler;
import gov.usgs.plot.DataPointRenderer;
import gov.usgs.plot.FrameRenderer;
import gov.usgs.plot.LegendRenderer;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.PlotException;
import gov.usgs.plot.PointRenderer;
import gov.usgs.plot.Renderer;
import gov.usgs.plot.ShapeRenderer;
import gov.usgs.plot.SmartTick;
import gov.usgs.plot.TextRenderer;
import gov.usgs.plot.LegendRenderer.LegendEntry;
import gov.usgs.util.Log;
import gov.usgs.vdx.data.wave.plot.SliceWaveRenderer;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;

/**
 * Special kind of plot which renders set of components as one component
 * 
 * @author Max Kokoulin
 */
public class CombinedPlot extends Plot {
	public final static double fillValue = Double.NEGATIVE_INFINITY;
	private final static Logger logger = Log.getLogger("gov.usgs.valve3.CombinedPlot"); 
	private int componentCount;
	private int callCount;
	private int leftTicks;
	private int graphX = Integer.MAX_VALUE;
	private int graphY = Integer.MAX_VALUE;
	private int graphWidth = Integer.MIN_VALUE;
	private int graphHeight = Integer.MIN_VALUE;
	private double minX = Double.MAX_VALUE;
	private double maxX = Double.MIN_VALUE;
	private String bottomLabel = null;
	private LegendRenderer legendRenderer = null;
	private ColorCycler cc = null;
	private RendererDataset leftRendererDataset  = new RendererDataset('L');
	private RendererDataset rightRendererDataset = new RendererDataset('R');
	private RendererDataset waveRendererDataset = new RendererDataset('W');
	private List<SliceWaveRenderer> waveRenderers = null; 

	/**
	 * Default constructor
	 */
	public CombinedPlot() {
		this(0, 0, 1);
	}

	/**
	 * Constructor
	 * @param w  plot width 
	 * @param h  plot height
	 * @param componentCount count of components
	 */
	public CombinedPlot(int w, int h, int componentCount) {
		super(w, h);
		this.componentCount = componentCount;
		callCount = 0;
		waveRenderers = new ArrayList<SliceWaveRenderer>();
	}

	/**
	 * Renders the plot: paints the background color, process
	 * renderers to combine and render resulting renderer.
	 * 
	 * @param g
	 *            the Graphics2D object to plot upon
	 */
	public void render(Graphics2D g) throws PlotException {
		callCount++;
		if (callCount == componentCount) {
			cc = new ColorCycler();
			for (Renderer renderer : renderers) {
				if (renderer instanceof MatrixRenderer) {
					MatrixRenderer matrixRenderer = (MatrixRenderer) renderer;
					String unit = getUnit(matrixRenderer);
					setColors(matrixRenderer.getLineRenderers());
					setColors(matrixRenderer.getPointRenderers());
					if(unit != null){
						if (leftRendererDataset.unit == null || unit.equals(leftRendererDataset.unit)) {
							combineRenderers(matrixRenderer, leftRendererDataset);
							//Merge legends
							legendRenderer = mergeLegendRenderer(legendRenderer, matrixRenderer.getLegendRenderer());
							if(matrixRenderer.getAxis().leftTicks.length > leftTicks){
								leftTicks = matrixRenderer.getAxis().leftTicks.length;
							}
						} else if (rightRendererDataset.unit == null || unit.equals(rightRendererDataset.unit)) {
							combineRenderers(matrixRenderer, rightRendererDataset);
							legendRenderer = mergeLegendRenderer(legendRenderer, matrixRenderer.getLegendRenderer());
						} else {
							throw new PlotException("Count of units is more than 2");
						}
					} else {
						throw new RuntimeException("Units not found");
					}
				} else if (renderer instanceof SliceWaveRenderer){
					SliceWaveRenderer waveRenderer = (SliceWaveRenderer) renderer;
					waveRenderer.setColor(cc.getNextColor());
					for(LegendRenderer.LegendEntry le: waveRenderer.getLegendRenderer().entries){
						if(le.lineRenderer != null){
							le.lineRenderer.color = waveRenderer.getColor();
						}
						if(le.pointRenderer != null){
							le.pointRenderer.color = waveRenderer.getColor();
						}
					}
					legendRenderer = mergeLegendRenderer(legendRenderer, waveRenderer.getLegendRenderer());
					setBoundaries(waveRenderer, waveRendererDataset);
					waveRenderers.add(waveRenderer);
				}
				else {	
					throw new RuntimeException("Unsupported renderer type in combined plot");
				}
			}
			renderers.clear();
			boolean firstRenderer = true;
			for(SliceWaveRenderer waveRenderer: waveRenderers){
				waveRenderer.setLocation(graphX, graphY, graphWidth, graphHeight);
				waveRenderer.setExtents(minX, maxX,	waveRendererDataset.minY, waveRendererDataset.maxY);
				if(firstRenderer){
					waveRenderer.update();
					waveRenderer.setLegendRenderer(legendRenderer);
					firstRenderer = false;
				} else {
					AxisRenderer ar = new AxisRenderer(waveRenderer);
					waveRenderer.setAxis(ar);
					waveRenderer.setLegendRenderer(null);
				}
				if(leftRendererDataset.unit==null){
					addRenderer(waveRenderer);
				}
				else if (rightRendererDataset.unit==null){
					waveRenderer.setLegendRenderer(null);
					AxisRenderer ar = new AxisRenderer(waveRenderer);
					ar.createRightTickLabels(SmartTick.autoTick(waveRenderer.getMinY(), waveRenderer.getMaxY(), 8, false), null);
					waveRenderer.setAxis(ar);
					addRenderer(waveRenderer);
				}
				else {
					throw new RuntimeException("Count of units is more than 2");
				}
			}
			if(leftRendererDataset.unit!=null){
				MatrixRenderer leftRenderer = createRenderer(leftRendererDataset);
				if (rightRendererDataset.unit == null){
					leftRenderer.setLegendRenderer(legendRenderer);
					addRenderer(leftRenderer);
				} else	{
					if(waveRenderers.size()>0 ){
						throw new RuntimeException("Count of units is more than 2");
					}
					MatrixRenderer rightRenderer = createRenderer(rightRendererDataset);
					rightRenderer.setLegendRenderer(legendRenderer);
					addRenderer(leftRenderer);
					addRenderer(rightRenderer);
				}
			}
			super.render(g);
		}
	}
	
	private String getUnit(MatrixRenderer renderer){
		Renderer label = renderer.getAxis().getLeftLabel();
		String unit = label==null?null:((TextRenderer) label).text;
		if(unit==null){
			label = renderer.getAxis().getRightLabel();
			unit = label==null?null:((TextRenderer) label).text;
		}
		return unit;
	}
	
	private String getBottom(MatrixRenderer renderer){
		Renderer bottom = renderer.getAxis().getBottomLabel();
		return bottom==null?null:((TextRenderer) bottom).text;
	}

	/**
	 * Combine two matrix renderers
	 * @param matrixRenderer MatrixRenderer to merge in
	 * @param rendererDataset RendererDataset to merge in
	 */
	private void combineRenderers(MatrixRenderer matrixRenderer, RendererDataset rendererDataset) {
		setBoundaries(matrixRenderer, rendererDataset);
		// Merge data
		DoubleMatrix2D data = null;
		if (matrixRenderer.getOffset() == 1) {
			// Shift to free column 1 for ranks
			data = new DenseDoubleMatrix2D(matrixRenderer.getData().rows(),	matrixRenderer.getData().columns() + 1);
			for (int row = 0; row < matrixRenderer.getData().rows(); row++) {
				for (int column = 0; column < matrixRenderer.getData().columns(); column++) {
					if (column == 0) {
						data.set(row, column, matrixRenderer.getData().get(row,	column));
					} else {
						data.set(row, column + 1, matrixRenderer.getData().get(row, column));
					}
				}
			}
		} else {
			data = matrixRenderer.getData();
		}
		rendererDataset.combinedData = mergeData(rendererDataset.combinedData, data);
		
		// Merge line renderers
		rendererDataset.lineRenderers = mergeLineRenderers(rendererDataset.lineRenderers, matrixRenderer.getLineRenderers(), data.columns() - 2);

		// Merge point renderers
		rendererDataset.pointRenderers = mergePointRenderers(rendererDataset.pointRenderers, (DataPointRenderer[]) matrixRenderer.getPointRenderers(), data.columns() - 2);
		
		rendererDataset.visible = mergeVisible(rendererDataset.visible, matrixRenderer.getVisible());
		
		if(rendererDataset.unit == null){
			rendererDataset.unit = getUnit(matrixRenderer);
		}
		if(bottomLabel == null){
			bottomLabel = getBottom(matrixRenderer);
		} else {
			//if(!bottomLabel.equals(getBottom(matrixRenderer))){
			//	throw new RuntimeException("Different units for X axis");
			//}
		}
	}
	
	/**
	 * Set max and min values, plot boundaries during renderers combination
	 * @param renderer FrameRenderer to merge in
	 * @param rendererDataset RendererDataset to merge in
	 */
	private void setBoundaries(FrameRenderer renderer, RendererDataset rendererDataset){
		if (renderer.getGraphX() < graphX) {
			graphX = renderer.getGraphX();
		}
		if (renderer.getGraphY() < graphY) {
			graphY = renderer.getGraphY();
		}
		if (renderer.getGraphWidth() > graphWidth) {
			graphWidth = renderer.getGraphWidth();
		}
		if (renderer.getGraphHeight() > graphHeight) {
			graphHeight = renderer.getGraphHeight();
		}
		if (renderer.getMinX() < minX) {
			minX = renderer.getMinX();
		}
		if (renderer.getMaxX() > maxX) {
			maxX = renderer.getMaxX();
		}
		if (renderer.getMinY() < rendererDataset.minY) {
			rendererDataset.minY = renderer.getMinY();
		}
		if (renderer.getMaxY() > rendererDataset.maxY) {
			rendererDataset.maxY = renderer.getMaxY();
		}
	}

	/**
	 * Merge two data matrix according MatrixRenderers data storing rules
	 * @param matrix DoubleMatrix2D to merge with
	 * @param matrixToAdd DoubleMatrix2D to add
	 * @return merged result
	 */
	private DoubleMatrix2D mergeData(DoubleMatrix2D matrix,
			DoubleMatrix2D matrixToAdd) {
		if (matrix == null) {
			return matrixToAdd;
		} else if (matrixToAdd == null) {
			return matrix;
		} else {
			logger.info("Result size: "
					+ (matrix.rows() + matrixToAdd.rows()) + "x"
					+ (matrix.columns() + matrixToAdd.columns() - 2));
			DoubleMatrix2D result = new DenseDoubleMatrix2D(matrix.rows()
					+ matrixToAdd.rows(), matrix.columns()
					+ matrixToAdd.columns() - 2);

			for (int row = 0; row < matrix.rows(); row++) {
				for (int column = 0; column < result.columns(); column++) {
					if (column < matrix.columns())
						result.set(row, column, matrix.get(row, column));
					else
						result.set(row, column, fillValue);
				}
			}
			for (int row = 0; row < matrixToAdd.rows(); row++) {
				for (int column = 0; column < result.columns(); column++) {
					if (column <= 1) {
						// fill time and rank
						result.set(row + matrix.rows(), column, matrixToAdd.get(row, column));
					} else if (column < matrix.columns()) {
						result.set(row + matrix.rows(), column, fillValue);
					} else {
						// logger.info("Setting cell: " +
						// (row+matrix.rows()) +"x" + (column) +
						// ", got from cell " + row + "x" +
						// (column-matrix.columns()+2));
						result.set(row + matrix.rows(), column, matrixToAdd.get(row, column - matrix.columns() + 2));
					}
				}
			}
			return result.viewSorted(0);
		}
	}
	
	private ShapeRenderer[] setColors(ShapeRenderer[] srs){
		if(srs!=null){
			for(ShapeRenderer sr: srs){
				if(sr!=null){
					sr.color = cc.getNextColor();
				}
			}
		}
		return srs;
	}
	
	private PointRenderer[] setColors(PointRenderer[] dprs){
		if(dprs!=null){
			for(PointRenderer dpr: dprs){
				if(dpr!=null){
					dpr.color = cc.getNextColor();
				}
			}
		}
		return dprs;
	}

	/**
	 * Merge LineRenderers according MatrixRenderers data storing rules
	 * @param array array of ShapeRenderers to merge with
	 * @param arrayToAdd array of ShapeRenderers to add
	 * @param dataColumnsCount default count of resulting array - it used in the case of nulls as merged arrays
	 * @return merged result
	 */
	// Maybe it is possible to write the following functions via generics. But I
	// don't know how to create resulting T[] if both array arguments are nulls.
	private ShapeRenderer[] mergeLineRenderers(ShapeRenderer[] array,
			ShapeRenderer[] arrayToAdd, int dataColumnsCount) {
		if (array == null) {
			if (arrayToAdd == null) {
				return new ShapeRenderer[dataColumnsCount];
			} else {
				return arrayToAdd;
			}
		} else {
			int resultSize = 0;
			if (arrayToAdd == null) {
				resultSize = array.length + dataColumnsCount;
			} else {
				resultSize = array.length + arrayToAdd.length;
			}
			ShapeRenderer[] result = new ShapeRenderer[resultSize];
			for (int i = 0; i < array.length; i++) {
				result[i] = array[i];
			}
			if (arrayToAdd != null) {
				for (int i = 0; i < arrayToAdd.length; i++) {
					result[i + array.length] = arrayToAdd[i];
				}
			}
			return result;
		}
	}
	


	/**
	 * Merge DataPointRenderers according MatrixRenderers data storing rules
	 * @param array array of DataPointRenderers to merge with
	 * @param arrayToAdd array of DataPointRenderers to add
	 * @param dataColumnsCount default count of resulting array - it used in the case of nulls as merged arrays
	 * @return merged result
	 */
	private DataPointRenderer[] mergePointRenderers(DataPointRenderer[] array,
			DataPointRenderer[] arrayToAdd, int dataColumnsCount) {
		if (array == null) {
			if (arrayToAdd == null) {
				return new DataPointRenderer[dataColumnsCount];
			} else {
				return arrayToAdd;
			}
		} else {
			int resultSize = 0;
			if (arrayToAdd == null) {
				resultSize = array.length + dataColumnsCount;
			} else {
				resultSize = array.length + arrayToAdd.length;
			}
			DataPointRenderer[] result = new DataPointRenderer[resultSize];
			for (int i = 0; i < array.length; i++) {
				result[i] = array[i];
			}
			if (arrayToAdd != null) {
				for (int i = 0; i < arrayToAdd.length; i++) {
					result[i + array.length] = arrayToAdd[i];
				}
			}
			return result;
		}
	}
	
	/**
	 * Merge LegendRenderers according MatrixRenderers data storing rules
	 * @param lrOne first LegendRenderer
	 * @param lrTwo second LegendRenderer
	 * @return merged result
	 */
	private LegendRenderer mergeLegendRenderer(LegendRenderer lrOne, LegendRenderer lrTwo) {
		if (lrOne == null) {
			return lrTwo;
		} else if (lrTwo == null) {
			return lrOne;
		} else {
			LegendRenderer result = new LegendRenderer();
			result.x	= graphX + 6;
		    result.y	= graphY + 6;
		    for (LegendEntry e : lrOne.entries) {
	            result.addLine(e);
	        }
		    for (LegendEntry e : lrTwo.entries) {
	            result.addLine(e);
	        }
			return result;
		}
	}

	/**
	 * Merge data column visibility flags according MatrixRenderers data storing rules
	 * @param arrayOne first visible array
	 * @param arrayTwo second visible array
	 * @return merged result
	 */
	private boolean[] mergeVisible(boolean[] arrayOne, boolean[] arrayTwo) {
		if (arrayOne == null) {
			return arrayTwo;
		} else if (arrayTwo == null) {
			return arrayOne;
		} else {
			boolean[] result = new boolean[arrayOne.length + arrayTwo.length];
			for (int i = 0; i < arrayOne.length; i++) {
				result[i] = arrayOne[i];
			}
			for (int i = 0; i < arrayTwo.length; i++) {
				result[i + arrayOne.length] = arrayTwo[i];
			}
			return result;
		}
	}
	
	/**
	 * Create combined MatrixRenderer from dataset computed from all processed separated MaxtrixRenderers
	 * @param rendererDataset dataset
	 */
	private MatrixRenderer createRenderer(RendererDataset rendererDataset) {
		MatrixRenderer renderer = new MatrixRenderer(rendererDataset.combinedData.viewSorted(0), true);
		renderer.setLineRenderers(rendererDataset.lineRenderers);
		for (DataPointRenderer pointRenderer : rendererDataset.pointRenderers) {
			if (pointRenderer != null) {
				pointRenderer.transformer = renderer;
				//System.out.println("ShapeRenderer color: " + pointRenderer.color);
			}
		}
		renderer.setPointRenderers(rendererDataset.pointRenderers);
		for (int i = 0; i < rendererDataset.visible.length; i++) {
			renderer.setVisible(i, rendererDataset.visible[i]);
		}
		renderer.setLocation(graphX, graphY, graphWidth, graphHeight);
		double extended_minY = rendererDataset.minY  - 0.02*(rendererDataset.maxY-rendererDataset.minY);
		double extended_maxY = rendererDataset.maxY  + 0.02*(rendererDataset.maxY-rendererDataset.minY);
		renderer.setExtents(minX, maxX,	extended_minY, extended_maxY);
		if(rendererDataset.type == 'L'){
			renderer.createDefaultAxis(8, 8, true, true, false, true, true, true);
			renderer.getAxis().setBackgroundColor(null);
			renderer.setXAxisToTime(8);
			renderer.getAxis().setLeftLabelAsText(rendererDataset.unit);
		} else if (rendererDataset.type == 'R'){
			AxisRenderer ar = new AxisRenderer(renderer);
			ar.setBackgroundColor(null);
			ar.createRightTickLabels(SmartTick.autoTick(extended_minY, extended_maxY, leftTicks, false), null);
			renderer.setAxis(ar);
			renderer.getAxis().setRightLabelAsText(rendererDataset.unit);
		}
		renderer.getAxis().setBottomLabelAsText(bottomLabel);	
		return renderer;
	}

	/**
	 * Class to store data about processed renderer
	 * @author Max Kokoulin
	 */
	class RendererDataset {
		char type = 'L';
		String unit = null;
		ShapeRenderer[] lineRenderers = null;
		DataPointRenderer[] pointRenderers = null;
		boolean[] visible = null;
		DoubleMatrix2D combinedData = null;

		double minY = Double.MAX_VALUE;
		double maxY = Double.MIN_VALUE;
		
		public RendererDataset(char type){
			this.type = type;
		}
	}
}
