/** @fileoverview  
 * 
 * function menu for titlemenu.html 
 *
 * @author Dan Cervelli
 */

/**
  *  Called for tiltmenu.html, allows access to form elements via menu object
  *  Sets up time shortcut values for popup.
  *
  *  @param {menu object} menu 
  */
create_tiltmenu = function(menu) {
	
	menu.allowChannelMap	= true;
	menu.formName			= "tiltForm";
	menu.boxName			= "tiltBox";

	// override the default initialize function because additional functionality needs to be setup
	menu.initialize = function() {
		
		// default initialization
		Menu.prototype.initialize.call(this);	
		
		// populate columns
		populateTiltColumns(this);
		
		// initialize the time shortcuts
		if (menu.timeShortcuts[0] == "") {
			menu.timeShortcuts	= new Array("-1h", "-2h", "-6h", "-12h", "-24h", "-2d", "-3d", "-1w", "-2w", "-1m");
		}
	}
	
	// override the default presubmit function
	menu.presubmit = function(pr, pc) {
		
		// call the presubmit function		
		return Menu.prototype.presubmit.call(this);
	}
}