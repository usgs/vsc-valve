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
	menu.formName			= "ratsamForm";
	menu.boxName			= "ratsamBox";	

	// override the default initialize function because additional functionality needs to be setup
	menu.initialize = function() {
		
		// default initialization
		Menu.prototype.initialize.call(this);	
		
		// initialize the time shortcuts
		if (menu.timeShortcuts[0] == "") {
			menu.timeShortcuts	= new Array("-10i","-30i","-1h","-6h","-12h","-1d","-3d","-1w","-2w","-1m");
		}
		
		var sel = document.getElementById(this.id + '_selector:ds');		
		addListener(sel, 'change', 
			function() {
				var interval = document.getElementById(this.id + '_interval');
				switch(sel.selectedIndex)
				{
					case 0:
						interval.firstChild.textContent = "Interval:";
						break;
					case 1:
						interval.firstChild.textContent = "Interval, pts: ";
						break;
					case 2:
						interval.firstChild.textContent = "Interval, sec: ";
						break;
				}
			}, false);
	}
	
	menu.presubmit = function(pr, pc) {	;
		
		var form	= this.getForm();
		var select	= form.elements["selector:ch"];
		var selstr	= getSelected(select, false);
		var selarr	= selstr.split("^");
			
		if (selarr.length != 2) {
			alert("You must select two channels.");
			return false;
		}
		
		// reset the plot height to indicate only one plot will be displayed
		var sizeIndex	= document.getElementById("outputSize").selectedIndex;
		var sizes		= STANDARD_SIZES[sizeIndex];
		pr.params.h	= sizes[1];
		
		// call the main presubmit function
		if (!Menu.prototype.presubmit.call(this)) {
			return false;
		} else {		
			return true;
		}
	}
}
