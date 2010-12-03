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
		
		// populate suppdata types
		populateSuppDataTypes(this);

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
	
	// override the default presubmit function
	menu.presubmit = function(pr, pc) {
		
		// call the presubmit function		
		return Menu.prototype.presubmit.call(this);
	}
}