// $Id: catalogmenu.js,v 1.1 2005/09/03 19:18:35 dcervelli Exp $
/*
 * Design note: can't use 'this' in functions passed to addListener.
 */
/** @fileoverview  
 * 
 * function menu for catalogmenu.html 
 *
 * @author Dan Cervelli
 */


/**
  *  Called for catalogmenu.html, allows access to form elements via menu object.
  *  Sets up time shortcut values for popup.
  *  
  *  Initilizes entry for Area of Interest NWES/ Depth Range min max/ Magnitude Filter min max/
  *  Output Type map view counts/ Options map view axes color counts, etc.
  *  
  *  Set up acceptMapClick to get latitude/longitude translations from web coordinates
  *  
  *  Filter by areas of interest.
  *  
  *  @param {menu object} menu 
 */
create_catalogmenu = function(menu)
{
	menu.formName = "catalogForm";
	menu.boxName = "catalogBox";
	menu.timeShortcuts = new Array("-2h", "-6h", "-12h", "-24h", "-3d", "-1w", "-1m", "-3m", "-6m", "-1y", "-2y", "-5y", "-10y", "-25y");
	
	menu.filter = new Object();
	menu.getFilter = function()
	{
		this.filter.fw = document.getElementById(this.id + '_west');
		this.filter.fe = document.getElementById(this.id + '_east');
		this.filter.fs = document.getElementById(this.id + '_south');
		this.filter.fn = document.getElementById(this.id + '_north');
		this.filter.fmind = document.getElementById(this.id + '_minDepth');
		this.filter.fmaxd = document.getElementById(this.id + '_maxDepth');
		this.filter.fminm = document.getElementById(this.id + '_minMag');
		this.filter.fmaxm = document.getElementById(this.id + '_maxMag');
		return this.filter;
	}
	
	menu.clickCount = 0;
	menu.acceptMapClick = function(target, mx, my, lon, lat)
	{
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

	menu.filterChanged = function()
	{
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
	
	menu.menuFocused = function()
	{
		this.filterChanged();
	}
		
	menu.initialize = function()
	{
		activateBox("catalogBox", this.id);
		var uf = document.getElementById(this.id + '_useGeoFilter');
		addListener(uf, 'click', function() { menu.clickCount = 0; menu.filterChanged(); }, false);
		
		//var zin = document.getElementById(menuid + '_zoomin');
		//var zout = document.getElementById(menuid + '_zoomout');
		//addListener(zout, 'click', function() { menu.zoom(1.5) }, false);
		//addListener(zin, 'click', function() { menu.zoom(0.667) }, false);
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
