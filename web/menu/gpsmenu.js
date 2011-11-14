/** @fileoverview  
 * 
 * function menu for gpsmenu.html 
 *
 * @author Dan Cervelli, Loren Antolik
 */

/**
 *  Called for gpsmenu.html, allows access to form elements via menu object
 *  Sets up time shortcut values for popup.
 *  
 *  Some checking is done in the menu here to disallow invalid choices.
 *  
 *  Add some listeners for the Set Baseline and Clear buttons.
 *  
 *  @param {menu object} menu 
 */
create_gpsmenu = function(menu) {
	menu.formName			= "gpsForm";
	menu.boxName			= "gpsBox";

	// override the default initialize function because additional functionality needs to be setup
	menu.initialize = function() {
		
		// default initialization
		Menu.prototype.initialize.call(this);	
		
		// populate columns
		populateGPSColumns(this);
		
		// populate suppdata types
		populateSuppDataTypes(this);
		
		// initialize the time shortcuts
		if (menu.timeShortcuts[0] == "") {
			menu.timeShortcuts	= new Array("-1m", "-3m", "-6m", "-1y", "-2y", "-3y");
		}
		
		var f	= this.getForm();
		var ch	= f["selector:ch"];
		
		addListener(document.getElementById(this.id + '_baselineButton'), 'click', 
			function() {
				if (ch.selectedIndex != -1)
					f.bl.value = ch[ch.selectedIndex].text;
			}, false);
			
		addListener(document.getElementById(this.id + '_clearButton'), 'click', 
			function() {
				f.bl.value = "[none]";
			}, false);

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

	menu.presubmit = function(pr, pc) {
		
		var f			= this.getForm();
		var select		= this.allChannels;
		var baseline	= f.bl;
		
		// if a baseline was selected, update it's value to the cid found in the select list, element 0
		if (baseline.value != '[none]') {
			for (var i = 0; i < this.allChannels.length; i++) {
				var s = this.allChannels[i].value.split(":");
				if (s[1] == baseline.value) {
					pc.bl = s[0];
				}
			}
		}
		
		// call the main presubmit function
		if (!Menu.prototype.presubmit.call(this)) {
			return false;
		} else {		
			return true;
		}
	}
}