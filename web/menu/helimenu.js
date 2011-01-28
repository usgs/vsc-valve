// $Id: helimenu.js,v 1.1 2005-09-03 19:18:35 dcervelli Exp $
/** @fileoverview  
 * 
 * function menu for helimenu.html 
 *
 * @author Dan Cervelli
 */

/**
 *  Allows access to form elements via menu object
 *  Sets up time shortcut values for popup.
 *  
 *  This creates the menu that allows you to generate helicorder images, which show 
 *  waveforms half hour per line, all lined up.
 *  
 *  The size of the helicorder changes based on how much time is selected, as calculated
 *  below. The web browser may not be able to display all the time chose for 1 week, say.
 *  
 *  @param {menu object} menu 
 */
create_helimenu = function(menu) {
	menu.formName			= "heliForm";
	menu.boxName			= "heliBox";
	menu.SIZES				= new Array(8, 10, 12, 14);

	// override the default initialize function because additional functionality needs to be setup
	menu.initialize = function() {
		
		// default initialization
		Menu.prototype.initialize.call(this);	
		
		// initialize the time shortcuts
		if (menu.timeShortcuts[0] == "") {
			menu.timeShortcuts	= new Array("-1h","-6h","-12h","-1d","-3d","-1w");
		}
	}
	
	menu.presubmit = function(pr, pc) {	
		
		// call the presubmit function
		Menu.prototype.presubmit.call(this);
		
		this.setSize(pr, pc);
		
		return true;
	}
		
	menu.setSize = function(pr, pc) {
		var si		= document.getElementById("outputSize").selectedIndex;
		var size	= this.SIZES[si];
		var dt		= timeDiff(pc.st, pc.et);
		var rows	= Math.ceil(dt / (pc.tc * 60000));
		pr.params.h	= pc.chCnt * (size * rows) + 60;
		pc.h		= pc.chCnt * (size * rows);
		if (si == 0) {
			pc.min = "T";
			pr.params.h -= 30;
		}
	}
}
