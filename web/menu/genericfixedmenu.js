/** @fileoverview  
 * 
 * function menu for genericfixedmenu.html 
 *
 * @author Dan Cervelli, Loren Antolik
 */

/**
 *  Called for genericfixedmenu.html, allows access to form elements via menu object
 *  Sets up time shortcut values for popup.
 *  
 *  Also, this tries to create and display a menu defined by an XML file for this
 *  generic data source.
 *  
 *  @param {menu object} menu 
 */
create_genericfixedmenu = function(menu) {
	
	menu.allowChannelMap	= true;
	menu.formName			= "genericFixedForm";
	menu.boxName			= "genericFixedBox";
	
	// override the default initialize function because the columns box needs to be populated here
	menu.initialize = function() {
		
		// default initialization
		Menu.prototype.initialize.call(this);	
		
		// populate columns
		populateGenericColumns(this);
		
		// initialize the time shortcuts
		if (menu.timeShortcuts[0] == "") {
			menu.timeShortcuts	= new Array("-1h","-1d","-1w","-1m","-1y");
		}
	}
	
	// override the default presubmit function
	menu.presubmit = function(pr, pc) {
		
		// call the presubmit function		
		return Menu.prototype.presubmit.call(this);
	}
}