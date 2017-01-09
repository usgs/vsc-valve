/** @fileoverview  
 * 
 * function menu for lightningmenu.html 
 *
 * @author Dan Cervelli
 */

/**
  *  Called for lightningmenu.html, allows access to form elements via menu object.
  *  Sets up time shortcut values for popup.
  *  
  *  Initilizes entry for Area of Interest NWES/ Depth Range min max/ Magnitude Filter min max/
  *  Output Type map view counts/ Options map view axes color counts, etc.
  *  
  *  Set up acceptMapClick to get latitude/longitude translations from web coordinates
  *  
  *  Filter by areas of interest.
  *  
  *  Design note: can't use 'this' in functions passed to addListener.
  *  
  *  @param {menu object} menu 
 */
create_lightningmenu = function(menu) {
	
	menu.formName	= "lightningForm";
	menu.boxName	= "lightningBox";

	// override the default initialize function because additional functionality needs to be setup
	menu.initialize = function() {
		
		// default initialization
		Menu.prototype.initialize.call(this);
		
		// initialize the time shortcuts
		if (menu.timeShortcuts[0] == "") {
			menu.timeShortcuts	= new Array("-2h", "-6h", "-12h", "-1d", "-3d", "-1w", "-1m", "-3m", "-6m", "-1y", "-2y", "-5y", "-10y", "-25y");
		}	
		
		activateBox("lightningBox", this.id);
		var uf = document.getElementById(this.id + '_useGeoFilter');
		addListener(uf, 'click', function() { menu.clickCount = 0; menu.filterChanged(); }, false);
	}
	
	menu.presubmit = function(pr, pc) {	
	
		// define variables from lightning menu
		var north	= parseFloat(document.getElementById(menu.id + "_north").value);
		var south	= parseFloat(document.getElementById(menu.id + "_south").value);
		var east	= parseFloat(document.getElementById(menu.id + "_east").value);
		var west	= parseFloat(document.getElementById(menu.id + "_west").value);

		if (isNaN(north) || isNaN(south) || isNaN(east) || isNaN(west)) {
			alert("Area of Interest must be populated with numeric values.");
			return false;
		}
		
		// call the main presubmit function
		if (!Menu.prototype.presubmit.call(this)) {
			return false;
			
		// check for north > south 
		} else if (north < south) {
			alert("North must be greater than South.");
			return false;
			
		} else {		
			return true;
		}
	}
	
	menu.filter = new Object();
	
	menu.getFilter = function() {
		this.filter.fw = document.getElementById(this.id + '_west');
		this.filter.fe = document.getElementById(this.id + '_east');
		this.filter.fs = document.getElementById(this.id + '_south');
		this.filter.fn = document.getElementById(this.id + '_north');
		return this.filter;
	}
	
	menu.clickCount = 0;
	menu.acceptMapClick = function(target, mx, my, lon, lat) {
		var filter = this.getFilter();
		if (this.clickCount % 2 == 0)
		{
			filter.fw.value = lon;
			filter.fn.value = lat;
		}
		else
		{
			filter.fe.value = lon;
			filter.fs.value = lat;
		}
		this.clickCount++;
	}

	menu.filterChanged = function() {
		if (this.clickCount > 0)
			return;
			
		var f = document.getElementById("geoFilter");
		var wesn = f[f.selectedIndex].value;
		if (wesn != "[none]")
		{
			wesn = wesn.split(",");
			var filter = this.getFilter();
			filter.fw.value = wesn[0];
			filter.fe.value = wesn[1];
			filter.fs.value = wesn[2];
			filter.fn.value = wesn[3];
		}
	}
	
	menu.menuFocused = function() {
		this.filterChanged();
	}
		
	/*
	menu.zoom = function(factor)
	{
		var w = document.getElementById('west');
		var e = document.getElementById('east');
		var s = document.getElementById('south');
		var n = document.getElementById('north');
		var xfilter = Math.abs(w.value - e.value) * factor;
		var xcenter = (w.value * 1 + e.value * 1) / 2;
		w.value = xcenter - (xfilter / 2);
		e.value = xcenter + (xfilter / 2);
		
		var yfilter = Math.abs(n.value - s.value) * factor;
		var ycenter = (s.value * 1 + n.value * 1) / 2;
		s.value = ycenter - (yfilter / 2);
		n.value = ycenter + (yfilter / 2);
		menu.submit();
	}
	*/
}
