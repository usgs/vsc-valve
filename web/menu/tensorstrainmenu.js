/** @fileoverview  
 * 
 * function menu for tensorstrainmenu.html 
 *
 * @author Max Kokoulin
 */

/**
  *  Called for tensorstrainmenu.html, allows access to form elements via menu object
  *  Sets up time shortcut values for popup.
  *
  *  @param {menu object} menu 
  */
create_tensorstrainmenu = function(menu) {
	menu.formName			= "tensorstrainForm";
	menu.boxName			= "tensorstrainBox";

	// override the default initialize function because additional functionality needs to be setup
	menu.initialize = function() {
		
		// default initialization
		Menu.prototype.initialize.call(this);	
		
		// populate columns
		populateTensorstrainColumns(this);
		
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
		var sel = document.getElementById(menu.id + '_channelselector');		
		addListener(sel, 'change', 
			function() {
				var label = document.getElementById(menu.id + '_naturallabel');
				var optionValue = sel.options[sel.selectedIndex].value;
				label.textContent = "Natural: " + optionValue.split(':')[6];
			}, false);
	}
	
	
	// override the default presubmit function
	menu.presubmit = function(pr, pc) {
		
		var count		= 0;
		var rotation_selected	= false;
		var namesElement	= document.getElementById(menu.id + "colNames");
		var namesCollection	= namesElement.getElementsByTagName('INPUT');

		for (var i = 0; i < namesCollection.length; i++) {
			if (((namesCollection[i].name.indexOf("eXXmeYY")!= -1) || (namesCollection[i].name.indexOf("eXY2")!= -1))&& namesCollection[i].checked) {
				rotation_selected = true;
			}
		}		
		var userRadio = document.getElementById(menu.id + "_az_u");
		var naturalRadio = document.getElementById(menu.id + "_az_n");
		
		if((!userRadio.checked && !naturalRadio.checked) && rotation_selected){
			alert("You must set rotation azimuth.");
			return false;
		}
		var userInput = document.getElementById(menu.id + "_azval");
		if(userRadio.checked && userInput.value==""){
			alert("You must enter custom rotation azimuth.");
			return false;
		}
		// call the presubmit function	
		return 	Menu.prototype.presubmit.call(this);;		
	}
}