// $Id: ratsammenu.js,v 1.1 2007/09/11 18:46:00 tparker Exp $
/** @fileoverview  
 * 
 * function menu for ratsammenu.html 
 *
 * @author Dan Cervelli
 */

/**
  *  Called for ratsammenu.html, RATSAM allows access to form elements via menu object
  *  Sets up time shortcut values for popup.  
  *  Populates the channels menu.
  *
  *  @param {menu object} menu 
  */
create_ratsammenu = function(menu) {
	
	menu.allowChannelMap	= true;
	menu.formName			= "ratsamForm";
	menu.boxName			= "ratsamBox";	

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
		
		var form	= this.getForm();
		var select	= form.elements["selector:ch"];
		var selstr	= getSelected(select, false);
		var selarr	= selstr.split("^");
			
		if (selarr.length != 2) {
			alert("You must select two channels.");
			return false;
		}
		
		// resize the box to make only one plot for two channels instead of two plots
		pr.params.h	= 150 + 60;
		pc.h		= 150;
		
		return true;
	}
}
