/** @fileoverview  
 * 
 * function menu for wavemenu.html 
 *
 * @author Dan Cervelli
 */

/**
  *  Called for wavemenu.html which deals with Waveforms data type,  it allows access 
  *  to form elements via menu object. *  Sets up time shortcut values for popup.
  *  
  *  This also listens for clicks to generate pop-up or "inset" plot requests 
  *  initiated by the user clicking on a waveform png.
  *  
  *  Accepting TY click allows first click to set start time and 2nd click to set end time 
  *  if Click Action is set to "Set Time" rather than "Popup". Otherwise a popup will be 
  *  shown for the duration shown in the drop-down men, default is 2 minutes.
  *  This makes an AJAX request for the necessary data.
  *
  *  @param {menu object} menu 
  */
create_wavemenu = function(menu) {
	menu.formName			= "waveForm";
	menu.boxName			= "waveBox";

	// override the default initialize function because additional functionality needs to be setup
	menu.initialize = function() {
		
		// default initialization
		Menu.prototype.initialize.call(this);	
		
		// initialize the time shortcuts
		if (menu.timeShortcuts[0] == "") {
			menu.timeShortcuts	= new Array("-1i", "-2i", "-5i", "-10i", "-20i", "-30i", "-1h");
		}

		// populate suppdata types
		populateSuppDataTypes(this);
	}
	
	menu.presubmit = function(pr, pc) {	
		
		// call the presubmit function
		Menu.prototype.presubmit.call(this);
		
		return true;
	}
	
	menu.acceptTYClick = function(target, mx, my, gx, gy) {
		var f = this.getForm();
		if (getChecked(f["skip:ca"]) == 0) {
			Menu.prototype.acceptTYClick.call(this, target, mx, my, gx, gy);
			return;
		}
		
		var pd = f["skip:popupDuration"];
		var span = pd[pd.selectedIndex].text * 1;
		var st = gx - (span / 2) * 60;
		var et = gx + (span / 2) * 60;
		var ch = getXMLField(target.xml, "ch");
		
		var pr = new PlotRequest(true);
		var pc = pr.createComponent(this.id, buildTimeString(st), buildTimeString(et));
		pc.setFromForm(f);
		pc.ch = ch;
		
		loadXML(this.id + " inset plot", pr.getURL(), 
			function(req) {
				var xml = req.responseXML;
				createPopupPlot(xml, mx, my);
			});
	}
}
