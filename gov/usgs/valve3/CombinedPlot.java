package gov.usgs.valve3;

import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.DataPointRenderer;
import gov.usgs.plot.FrameRenderer;
import gov.usgs.plot.LegendRenderer;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.Renderer;
import gov.usgs.plot.ShapeRenderer;
import gov.usgs.plot.SmartTick;
import gov.usgs.plot.TextRenderer;
import gov.usgs.plot.LegendRenderer.LegendEntry;
import gov.usgs.vdx.data.wave.plot.SliceWaveRenderer;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;

public class CombinedPlot extends Plot {
	public final static double fillValue = Double.NEGATIVE_INFINITY;
	// private List<PlotComponent> components = null;
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
	private RendererDataset leftRendererDataset  = new RendererDataset('L');
	private RendererDataset rightRendererDataset = new RendererDataset('R');
	private RendererDataset waveRendererDataset = new RendererDataset('W');
	private List<SliceWaveRenderer> waveRenderers = null; 

	public CombinedPlot() {
		this(0, 0, 1);
	}

	public CombinedPlot(int w, int h, int componentCount) {
		super(w, h);
		this.componentCount = componentCount;
		callCount = 0;
		waveRenderers = new ArrayList<SliceWaveRenderer>();
	}

	/**
	 * Renders the plot. This simply paints the background color, process
	 * renderers to combine and render resulting renderer.
	 * 
	 * @param g
	 *            the Graphics2D object to plot upon
	 */
	public void render(Graphics2D g) {
		callCount++;
		if (callCount == componentCount) {
			for (Renderer renderer : renderers) {
				if (renderer instanceof MatrixRenderer) {
					MatrixRenderer matrixRenderer = (MatrixRenderer) renderer;
					String unit = ((TextRenderer) matrixRenderer.getAxis().getLeftLabel()).text;
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
						throw new RuntimeException("Count of units is more than 2");
					}
				} else if (renderer instanceof SliceWaveRenderer){
					SliceWaveRenderer waveRenderer = (SliceWaveRenderer) renderer;
					legendRenderer = mergeLegendRenderer(legendRenderer, waveRenderer.getLegendRenderer());
					waveRenderer.setLegendRenderer(null);
					setBoundaries(waveRenderer, waveRendererDataset);
					AxisRenderer ar = new AxisRenderer(waveRenderer);
					ar.createRightTickLabels(SmartTick.autoTick(waveRenderer.getMinY(), waveRenderer.getMaxY(), 8, false), null);
					waveRenderer.setAxis(ar);
					waveRenderer.getAxis().setRightLabelAsText("Counts");
					waveRenderers.add(waveRenderer);
				}
				else {	
					throw new RuntimeException("Unsupported renderer type in combined plot");
				}
			}
			renderers.clear();
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
			for(SliceWaveRenderer waveRenderer: waveRenderers){
				waveRenderer.setLocation(graphX, graphY, graphWidth, graphHeight);
				waveRenderer.setExtents(minX, maxX,	waveRendererDataset.minY, waveRendererDataset.maxY);
				addRenderer(waveRenderer);
			}
			super.render(g);
		}
	}

	private void combineRenderers(MatrixRenderer matrixRenderer, RendererDataset rendererDataset) {
		setBoundaries(matrixRenderer, rendererDataset);
		// Merge data
		DoubleMatrix2D data = null;
		if (matrixRenderer.getOffset() == 1) {
			// Shift to free column 1 for ranks
			data = new DenseDoubleMatrix2D(matrixRenderer.getData().rows(),
					matrixRenderer.getData().columns() + 1);
			for (int row = 0; row < matrixRenderer.getData().rows(); row++) {
				for (int column = 0; row < matrixRenderer.getData().columns(); column++) {
					if (column == 0) {
						data.set(row, column, matrixRenderer.getData().get(row,
								column));
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
			rendererDataset.unit = ((TextRenderer) matrixRenderer.getAxis().getLeftLabel()).text;
		}
		if(bottomLabel == null){
			bottomLabel = ((TextRenderer) matrixRenderer.getAxis().getBottomLabel()).text;
		} else {
			if(!bottomLabel.equals(((TextRenderer) matrixRenderer.getAxis().getBottomLabel()).text)){
				throw new RuntimeException("Different units for X axis");
			}
		}
	}
	
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

	private DoubleMatrix2D mergeData(DoubleMatrix2D matrix,
			DoubleMatrix2D matrixToAdd) {
		if (matrix == null) {
			return matrixToAdd;
		} else if (matrixToAdd == null) {
			return matrix;
		} else {
			System.out.println("Result size: "
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
						result.set(row + matrix.rows(), column, matrixToAdd
								.get(row, column));
					} else if (column < matrix.columns()) {
						result.set(row + matrix.rows(), column, fillValue);
					} else {
						// System.out.println("Setting cell: " +
						// (row+matrix.rows()) +"x" + (column) +
						// ", got from cell " + row + "x" +
						// (column-matrix.columns()+2));
						result.set(row + matrix.rows(), column, matrixToAdd
								.get(row, column - matrix.columns() + 2));
					}
				}
			}
			return result.viewSorted(0);
		}
	}

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

	private MatrixRenderer createRenderer(RendererDataset rendererDataset) {
		MatrixRenderer renderer = new MatrixRenderer(rendererDataset.combinedData.viewSorted(0), true);
		renderer.setLineRenderers(rendererDataset.lineRenderers);
		for (DataPointRenderer pointRenderer : rendererDataset.pointRenderers) {
			if (pointRenderer != null) {
				pointRenderer.transformer = renderer;
			}
		}
		renderer.setPointRenderers(rendererDataset.pointRenderers);
		for (int i = 0; i < rendererDataset.visible.length; i++) {
			renderer.setVisible(i, rendererDataset.visible[i]);
		}
		renderer.setLocation(graphX, graphY, graphWidth, graphHeight);
		renderer.setExtents(minX, maxX,	rendererDataset.minY, rendererDataset.maxY);
		if(rendererDataset.type == 'L'){
			renderer.createDefaultAxis(8, 8, false, true, true);
			renderer.setXAxisToTime(8);
			renderer.getAxis().setLeftLabelAsText(rendererDataset.unit);
		} else if (rendererDataset.type == 'R'){
			AxisRenderer ar = new AxisRenderer(renderer);
			ar.createRightTickLabels(SmartTick.autoTick(rendererDataset.minY, rendererDataset.maxY, leftTicks, false), null);
			renderer.setAxis(ar);
			renderer.getAxis().setRightLabelAsText(rendererDataset.unit);
		}
		renderer.getAxis().setBottomLabelAsText(bottomLabel);	
		return renderer;
	}

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
