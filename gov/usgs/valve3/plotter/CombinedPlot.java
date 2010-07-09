package gov.usgs.valve3.plotter;

import gov.usgs.plot.DataPointRenderer;
import gov.usgs.plot.LegendRenderer;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.Renderer;
import gov.usgs.plot.ShapeRenderer;
import java.awt.Graphics2D;


import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;

public class CombinedPlot extends Plot {
	public final static double fillValue = Double.NEGATIVE_INFINITY;
	//private List<PlotComponent> components = null;
	private int componentCount;
	private int callCount;
	private ShapeRenderer[] lineRenderers = null;
	private DataPointRenderer[] pointRenderers = null;
	private LegendRenderer legendRenderer = null;
	private boolean[] visible = null;
	private DoubleMatrix2D combinedData = null;
	
    private int graphX = Integer.MAX_VALUE;
    private int graphY = Integer.MAX_VALUE;
    private int graphWidth = Integer.MIN_VALUE;
    private int graphHeight = Integer.MIN_VALUE;
    
    private double minX = Double.MAX_VALUE;
    private double maxX = Double.MIN_VALUE;
    private double minY = Double.MAX_VALUE;
    private double maxY = Double.MIN_VALUE;

    public CombinedPlot()
    {
        this(0, 0, 1);
    }
    
    public CombinedPlot(int w, int h, int componentCount)
    {
    	super(w,h);
    	this.componentCount = componentCount;
    	callCount = 0;
    }
	
	/** Renders the plot.  This simply paints the background color, process renderers to combine and render resulting renderer.
	 * @param g the Graphics2D object to plot upon
	 */
    public void render(Graphics2D g){
    	combineRenderers();
       	callCount++;
    	if(callCount==componentCount){
    		renderers.clear();
     	    MatrixRenderer combinedRenderer = new MatrixRenderer(combinedData.viewSorted(0), true);
    	    combinedRenderer.setLineRenderers(lineRenderers);
    	    combinedRenderer.setPointRenderers(pointRenderers);
    	    combinedRenderer.setLegendRenderer(legendRenderer);
    	    for(int i = 0; i<visible.length; i++){
    	    	combinedRenderer.setVisible(i, visible[i]);
    	    }
    	    combinedRenderer.setLocation(graphX, graphY, graphWidth, graphHeight);
    	    combinedRenderer.setExtents(minX, maxX, minY, maxY);
    	    //combinedRenderer.createDefaultAxis();
     	    addRenderer(combinedRenderer);
    		super.render(g);
    	}
     }
    
    private void combineRenderers(){
     	for(Renderer renderer: renderers){
      		if(!(renderer instanceof MatrixRenderer)){
    			throw new RuntimeException("Only MatrixRenderer supported for combined plots");
    		}

    		MatrixRenderer matrixRenderer = (MatrixRenderer)renderer;
      	    if(matrixRenderer.getGraphX() < graphX){
      	    	graphX = matrixRenderer.getGraphX();
      	    }
      	    if(matrixRenderer.getGraphY() < graphY){
    	    	graphY = matrixRenderer.getGraphY();
    	    }
     	    if(matrixRenderer.getGraphWidth() > graphWidth){
     	    	graphWidth = matrixRenderer.getGraphWidth();
    	    }
     	    if(matrixRenderer.getGraphHeight() > graphHeight){
     	    	graphHeight = matrixRenderer.getGraphHeight();
   	        }
     	    if(matrixRenderer.getMinX() < minX){
     	    	minX = matrixRenderer.getMinX();
     	    }
     	    if(matrixRenderer.getMaxX() > maxX){
    	    	maxX = matrixRenderer.getMaxX();
    	    }
     	    if(matrixRenderer.getMinY() < minY){
    	    	minY = matrixRenderer.getMinY();
    	    }
    	    if(matrixRenderer.getMaxY() > maxY){
    	    	maxY = matrixRenderer.getMaxY();
   	        }
     	    
       		//Merge data
       		DoubleMatrix2D data = null;
    		if(matrixRenderer.getOffset()==1){
    			//Shift to free column 1 for ranks
    			data = new DenseDoubleMatrix2D(matrixRenderer.getData().rows(), matrixRenderer.getData().columns()+1);
    			for(int row = 0; row<matrixRenderer.getData().rows(); row++){
    				for(int column = 0; row<matrixRenderer.getData().columns(); column++){
    					if(column==0){
    						data.set(row, column, matrixRenderer.getData().get(row, column));
    					} else {
    						data.set(row, column+1, matrixRenderer.getData().get(row, column));
    					}
    				}
    			}
    		} else {
    			data = matrixRenderer.getData();
    		}
   			combinedData = mergeData(combinedData, data);
 
    		//Merge line renderers
    		lineRenderers = mergeLineRenderers(lineRenderers, matrixRenderer.getLineRenderers(), data.columns()-2);
    		
    		//Merge point renderers
    		pointRenderers = mergePointRenderers(pointRenderers, (DataPointRenderer[])matrixRenderer.getPointRenderers(),  data.columns()-2);
    		
    		visible = mergeVisible(visible, matrixRenderer.getVisible());
    	}
    }
    
    private DoubleMatrix2D mergeData(DoubleMatrix2D matrix, DoubleMatrix2D matrixToAdd){
    	if(matrix==null){
    		return matrixToAdd;
    	} else if(matrixToAdd == null){
    		return matrix;
    	} else {
    		System.out.println("Result size: " + (matrix.rows() + matrixToAdd.rows()) + "x" + (matrix.columns() + matrixToAdd.columns()-2));
    		DoubleMatrix2D result = new DenseDoubleMatrix2D(matrix.rows() + matrixToAdd.rows(), matrix.columns() + matrixToAdd.columns()-2);
    		
    		for(int row = 0; row<matrix.rows(); row++){
    			for(int column = 0; column<result.columns(); column++){
    				if(column< matrix.columns())
    					result.set(row, column, matrix.get(row, column));
    				else
    					result.set(row, column, fillValue);
    			}
    		}
    		for(int row = 0; row<matrixToAdd.rows(); row++){
    			for(int column = 0; column<result.columns(); column++){
    				if(column<=1){
    					//fill time and rank
    					result.set(row+matrix.rows(), column, matrixToAdd.get(row, column));
    				} else if(column<matrix.columns()){
    					result.set(row+matrix.rows(), column, fillValue);
    				} else {
    					//System.out.println("Setting cell: " + (row+matrix.rows()) +"x" + (column) + ", got from cell " + row + "x" + (column-matrix.columns()+2));
     					result.set(row+matrix.rows(), column, matrixToAdd.get(row, column-matrix.columns()+2));
    				}
    			}
    		}
    		return result.viewSorted(0);
    	}
    }
    
    //Maybe it is possible to write the following functions via generics. But I don't know how to create resulting T[] if both array arguments are nulls. 
    private ShapeRenderer[] mergeLineRenderers(ShapeRenderer[] array, ShapeRenderer[]arrayToAdd, int dataColumnsCount){
    	if(array == null){
    		if(arrayToAdd==null){
        		return new ShapeRenderer[dataColumnsCount];
    		} else {
    			return arrayToAdd;
    		}
    	} else {
        	int resultSize = 0;
    	    if(arrayToAdd==null) {
    	    	resultSize = array.length+dataColumnsCount;
    	    } else {
    	    	resultSize = array.length + arrayToAdd.length;
    	    }
    	    ShapeRenderer[] result = new ShapeRenderer[resultSize];
    		for(int i = 0; i<array.length; i++){
    			result[i] = array[i];
    		}
    		if(arrayToAdd!=null){
    			for(int i = 0; i<arrayToAdd.length; i++){
    				result[i+array.length] = arrayToAdd[i];
    			}
    		}
    		return result;
    	}
     }
    
    private DataPointRenderer[] mergePointRenderers(DataPointRenderer[] array, DataPointRenderer[]arrayToAdd, int dataColumnsCount){
    	if(array == null){
    		if(arrayToAdd==null){
        		return new DataPointRenderer[dataColumnsCount];
    		} else {
    			return arrayToAdd;
    		}
    	} else {
        	int resultSize = 0;
    	    if(arrayToAdd==null) {
    	    	resultSize = array.length+dataColumnsCount;
    	    } else {
    	    	resultSize = array.length + arrayToAdd.length;
    	    }
    	    DataPointRenderer[] result = new DataPointRenderer[resultSize];
    		for(int i = 0; i<array.length; i++){
    			result[i] = array[i];
    		}
    		if(arrayToAdd!=null){
    			for(int i = 0; i<arrayToAdd.length; i++){
    				result[i+array.length] = arrayToAdd[i];
    			}
    		}
    		return result;
    	}
     }
    
    
    
    private boolean[] mergeVisible(boolean[] arrayOne, boolean[]arrayTwo){
     	if(arrayOne == null){
    		return arrayTwo;
    	} else if(arrayTwo==null) {
    		return arrayOne;
    	} else {
    		boolean[] result = new boolean[arrayOne.length + arrayTwo.length];
    		for(int i = 0; i<arrayOne.length; i++){
    			result[i] = arrayOne[i];
    		}
    		for(int i = 0; i<arrayTwo.length; i++){
    			result[i+arrayOne.length] = arrayTwo[i];
    		}
    		return result;
    	}
     }
}
