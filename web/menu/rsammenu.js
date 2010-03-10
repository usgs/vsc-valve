/** @fileoverview  
 * 
 * function menu for rsammenu.html 
 *
 * @author Dan Cervelli
 */

/**
  *  Called for rsammenu.html, RSAM data source, allows access to form elements via menu object
  *  Sets up time shortcut values for popup.
  *
  *  @param {menu object} menu 
  */
create_rsammenu = function(menu) {
	
	menu.allowChannelMap	= true;
	menu.formName			= "rsamForm";
	menu.boxName			= "rsamBox";

	// override the default initialize function because additional functionality needs to be setup
	menu.initialize = function() {
		
		// default initialization
		Menu.prototype.initialize.call(this);	
		
		// initialize the time shortcuts
		if (menu.timeShortcuts[0] == "") {
			menu.timeShortcuts	= new Array("-1h", "-2h", "-6h", "-12h", "-24h", "-2d", "-3d", "-1w", "-2w", "-1m");
		}
	}
	
	menu.presubmit = function(pr, pc) {	
		
		// call the presubmit function
		Menu.prototype.presubmit.call(this);
		
		return true;
	}
}
