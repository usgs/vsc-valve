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
	menu.formName			= "rsamForm";
	menu.boxName			= "rsamBox";

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
		
		sel = document.getElementById(this.id + '_showall');
		addListener(sel, 'change', function() {
			if (currentMenu)
				currentMenu.filterChanged();
		}, false);
	}
	
	menu.presubmit = function(pr, pc) {	
		
		// call the main presubmit function
		if (!Menu.prototype.presubmit.call(this)) {
			return false;
		} else {		
			return true;
		}
	}
}
