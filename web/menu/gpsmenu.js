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
		
		// filter channels based on default channel type filter
		Menu.prototype.filterChanged.call(this);
		
		// populate columns
		populateGPSColumns(this);
		
		// populate suppdata types
		populateSuppDataTypes(this);
		
		// initialize the time shortcuts
		if (menu.timeShortcuts[0] == "") {
			menu.timeShortcuts	= new Array("-1d", "-3d", "-1w", "-2w", "-1m", "-3m", "-6m", "-1y", "-3y", "-5y", "-10y", "-20y");
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
		
		var f			= this.getForm();
		var select		= this.allChannels;
		var baseline	= f.elements['selector:bl'];
		
		// if a baseline was selected, update it's value to the cid found in the select list, element 0
		if (baseline.value != '[none]') {
			var s = baseline.value.split(':');
			pc.bl = s[0];
		}
		
		// if displacement plot type, validate the displacement time
		if (f.plotType[2].checked) {
		
			// get the displacement times
			var bpst = getDisplacementTime(document.getElementById(this.id + '_displacementBeforeStartTime'), 'Before Period Start Time.');
			if (bpst == null) return false;
			
			var bpet = getDisplacementTime(document.getElementById(this.id + '_displacementBeforeEndTime'), 'Before Period End Time.');
			if (bpet == null) {
				return false;			
			} else if (bpet <= bpst) {
				alert('Before Period End Time must be > Before Period Start Time.');
				return false;
			}
			
			var apst = getDisplacementTime(document.getElementById(this.id + '_displacementAfterStartTime'), 'After Period Start Time.');
			if (apst == null) {
				return false;			
			} else if (apst <= bpet) {
				alert('After Period Start Time must be > Before Period End Time.');
				return false;
			}
			
			var apet = getDisplacementTime(document.getElementById(this.id + '_displacementAfterEndTime'), 'After Period End Time.');
			if (apet == null) {
				return false;			
			} else if (apet <= apst) {
				alert('After Period End Time must be > After Period Start Time.');
				return false;
			}

			// pc.displacementBeforeStartTime	= bpst;
			// pc.displacementBeforeEndTime	= bpet;
			// pc.displacementAfterStartTime	= apst;
			// pc.displacementAfterEndTime		= apet;
		}		
		
		// call the main presubmit function
		if (!Menu.prototype.presubmit.call(this)) {
			return false;
		} else {		
			return true;
		}
	}
}

/**
 *  grab the displacement time form field from the document.
 *  
 *  Confirm that it's a valid date/time, and then return it.
 *
 *  @return the displacement time as an integer
 *  @type int
 */
function getDisplacementTime(dt, fieldname)
{
	dt.value = dt.value.replace(/[\[\]\'\"]/g, "");
	var errMsg = "Invalid " + fieldname + ".";
	if (!validateDate(dt, true))
	{
		alert(errMsg);
		return null;
	}
	return dt.value;
}