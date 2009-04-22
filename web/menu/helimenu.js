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
create_helimenu = function(menu)
{
	menu.allowChannelMap = true;
	menu.formName = "heliForm";
	menu.boxName = "heliBox";
	menu.selector = "selector:ch";
	menu.timeShortcuts = new Array("-6h", "-12h", "-24h", "-2d", "-3d", "-1w");

	menu.SIZES = new Array(8, 10, 12, 14);
		
	menu.setSize = function(pr, pc)
	{
		var si = document.getElementById("outputSize").selectedIndex;
		var size = this.SIZES[si];
		var dt = timeDiff(pc.st, pc.et);
		var rows = Math.ceil(dt / (pc.tc * 60000));
		pr.params.h = size * rows + 60;
		pc.h = size * rows;
		if (si == 0)
		{
			pc.min = "T";
			pr.params.h -= 30;
		}
	}
	
	menu.presubmit = function(pr, pc)
	{
		this.setSize(pr, pc);
		return true;
	}
}
