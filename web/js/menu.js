// $Id: menu.js,v 1.11 2007/09/11 18:47:49 tparker Exp $
/** @fileoverview Functions in menu.js have to do with the text-based menus at the top of the Valve screen 
 *  @author Dan Cervelli
 */


var lastDiv = null;
var currentMenu = null;
/**
 *  Sets the currentMenu global variable to the menu parameter passed in
 *  Populate the time shortcut panel, but don't display it.
 *  Activate the geographical filter.
 *  
 *  @param {menu object} menu 
 */
function setCurrentMenu(menu)
{
	currentMenu = menu;
	populateTimeShortcuts(menu.timeShortcuts);
	menu.menuFocused();
}
/**
 *  Initiates the "AJAX" asynchronous load for the submenu div specified by the target(id) parameter.
 *  This new submenu then should appear on the screen to the right of the main menu. Clears any previous submenu.
 *
 *  @param {string} id
 */
function loadMenu(id)
{
	if (lastDiv)
		lastDiv.style.display = 'none';
		
	if (menus[id].div)
	{
		menus[id].div.style.display = 'block';
		lastDiv = menus[id].div;
		setCurrentMenu(menus[id]);
		return;
	}
		
	var url = "menu/" + menus[id].file + ".html";
	loadXML(menus[id].file + " javascript", url, 
		function(req) 
		{
			menus[id].html = req.responseText;
			var div = document.createElement('div');
			div.innerHTML = menus[id].html;
			document.getElementById('openPanel').appendChild(div);
			menus[id].div = div;
			lastDiv = div;
			loadXML(menus[id].file + " javascript", "menu/" + menus[id].file + ".js", function(req)
				{
					if (req.readyState == 4 && req.status == 200)
					{
						menus[id].js = req.responseText;
						eval(menus[id].js);
						eval('create_' + menus[id].file + '(menus[id], id)');
						menus[id].initialize();
						setCurrentMenu(menus[id]);
					}
				});
		});
}

var menus = new Array(10);
/**
 *  Set up the initial menu, populate data sources - current data, historical data, 
 *  geographic filter, start/end time, administrator email link, etc.
 *  
 *  @param {text} xml block of xml text
 */
function handleMenu(xml)
{
	var sections = xml.getElementsByTagName("section");
	var vp = document.getElementById('dataPanel');
	for (var i = 0; i < sections.length; i++)
	{
		var name = sections[i].getElementsByTagName("name")[0].firstChild.data;
		//var icon = sections[i].getElementsByTagName("icon")[0].firstChild.data;
	
		var t = document.getElementById('listItemTemplate').cloneNode(true);
		t.id = "section" + i;
		var header = t.getElementsByTagName('h1')[0];
		header.childNodes[1].nodeValue = ' ' + name;
		var img = t.getElementsByTagName('img')[0];
		addListener(img, 'click', function(e) { toggle(e); }, false);

		var items = sections[i].getElementsByTagName("menuitem");
		var ip = t.getElementsByTagName('ul')[0];
		for (var j = 0; j < items.length; j++)
		{
			var menuid = items[j].getElementsByTagName("menuid")[0].firstChild.data;
			var name = items[j].getElementsByTagName("name")[0].firstChild.data;
			var file = items[j].getElementsByTagName("file")[0].firstChild.data;
			
			var m = new Menu();
			m.id = menuid;
			m.file = file;
			menus[menuid] = m;
			var li = document.createElement('li');
			li.id = menuid;
			li.appendChild(document.createTextNode(name));
			addListener(li, 'click', function(e) { doSelect(e); }, false);
			addListener(li, 'mouseover', function(e) { doMouseOver(e); }, false);
			addListener(li, 'mouseout', function(e) { doMouseOut(e); }, false);
			
			ip.appendChild(li);
		}
		vp.getElementsByTagName('ul')[0].appendChild(t);
	}
	
	var inst = xml.getElementsByTagName("title")[0].firstChild.data;
	document.title = inst;
	
	document.getElementById('appTitle').appendChild(document.createTextNode(inst));
	document.getElementById('timeZoneAbbr').appendChild(document.createTextNode(getXMLField(xml, "timeZoneAbbr")));
	document.getElementById('timeZoneOffset').value = getXMLField(xml, "timeZoneOffset");
	setAdmin(getXMLField(xml, "administrator"), getXMLField(xml, "administrator-email"));
	document.getElementById('version').appendChild(document.createTextNode("Valve " + getXMLField(xml, "version")));
}

/**
 *  Load up the geographical filter drop-down menu from the filters.txt text file that has  
 *  lines like this:
 *      Alaska:172,-129.9,51,72
 *      Volcanic Arc:172,-151,51,62
 *      Adagdak:-176.955912,-176.228088,51.76331042,52.2126809
 *  Also, add a listener for menu selections.
 */
function loadFilters()
{
	var url = "filters.txt";
	loadXML("geographic filter", url, 
		function(req)
		{
			var f = document.getElementById("geoFilter");
			var lines = req.responseText.split("\n");
			for (var i = 0; i < lines.length; i++)
			{
				if (lines[i].charAt(0) == '#')
					continue;
				var line = lines[i].split(":");
				if (line.length <= 1)
					continue;
				var opt = document.createElement('option');
				opt.value = line[1];
				opt.appendChild(document.createTextNode(line[0]));
				f.appendChild(opt);
			}
			
			addListener(f, 'change', function(e) { if (currentMenu) currentMenu.filterChanged(); }, false);
		});
}

/**
 * Load main menu data sources.
 */
function loadDataSources()
{
	loadFilters();
	var	url = "valve3.jsp?a=menu";
	loadXML("main menu", url);
}

/** 
 *	Make an AJAX call via jsp to return the values for the Channels menu
 *  
 *  @param {menu object} menu 
 */
function populateSelectors(menu)
{
	var f = document.getElementById(menu.id + '_' + menu.formName);
	var select = f.elements[menu.selector];
	if (menu.secondSelector != undefined)
		var secondSelect = f.elements[menu.secondSelector];
	
	var url = "valve3.jsp?a=data&src=" + menu.id + "&da=selectors";
	loadXML(menu.id + " selectors", url, 
		function(req)
		{
			var xml = req.responseXML;
			var ch = xml.getElementsByTagName('list-item');
			var opts = new Array(ch.length);
			for (var i = 0; i < ch.length; i++)
			{
				var val = ch[i].firstChild.nodeValue;
				var ss = val.split(':');
				var opt = document.createElement('option');
				
				// Flipping this back will break NWIS. Don't do it.
				// set title like NWIS
				//opt.value = ss[3];
				opt.value = val;
				
				opt.appendChild(document.createTextNode(ss[3].replace(/\$/g,' ')));
				select.appendChild(opt);
				opts[i] = opt;

				if (secondSelect != undefined) 
				{
					var val = ch[i].firstChild.nodeValue;
					var ss = val.split(':');
					var opt = document.createElement('option');
					
					opt.value = val;
					
					opt.appendChild(document.createTextNode(ss[3].replace(/\$/g,' ')));
					select.appendChild(opt);
								
					secondSelect.appendChild(opt);
				}
			}
			f.options = opts;
			menu.filterChanged();
		});
}

/** 
 *	hide or show submenus via + or - icons. For example, the "options" submenu.
 *  
 *  @param {event object} event This is the event that causes the toggle.
 * 
 */
function toggle(event)
{
	var target = getTarget(event).parentNode.parentNode;
	var subMenu = target.getElementsByTagName("ul")[0];
	var img = target.getElementsByTagName("img")[0];
	if (subMenu.className == "hiddenSubMenu")
	{
		subMenu.className = "subMenu";
		img.src = "images/minus.png";
	}
	else
	{
		subMenu.className = "hiddenSubMenu";
		img.src = "images/plus.png";
	}
}

var lastSelected = null;
var wasSelected = false;
/**
 *  Select target menu item that has been clicked on, changing it's menu color to
 *  "selected" (see valve3.css), and clear the select color from any previously 
 *  selected menu item.
 *  
 *  Next load the menu associated with the target
 *
 *  @param {event object} e
 *  @param {element object} elt 
 */
function doSelect(e, elt)
{
	if (lastSelected != null)
		lastSelected.className = "";
	
	var target = getTarget(e);
	target.className = "selected";
	wasSelected = true;
	lastSelected = target;
	
	loadMenu(target.id);
}

/**
 *  Remember if an item in a menu is selected, if you hover over any 
 *  item it turns css "hover" which currently is light blue (see valve3.css)
 *
 *  @param {event object} e
 */
function doMouseOver(e)
{
	var target = getTarget(e);
	if (target.className == "selected")
		wasSelected = true;
	target.className = "hover";
}

/**
 *  On mousing out of a menu list item, either change the css class to show 
 *  the color for "selected" or the color for not selected and not hovered over
 *  in this case white (see valve3.css)
 *
 *  @param {event object} e
 */
function doMouseOut(e)
{
	var target = getTarget(e);
	if (wasSelected)
		target.className = "selected";
	else
		target.className = "";
	wasSelected = false;
}

/**
 *  Creates from this the text "Last minute", "Last two minutes" etc.
 *  
 *  @param {array} scs Example of scs array: "-1i", "-2i", "-5i", "-10i", "-20i", "-30i", "-1h"
 *  @return array of strings of time shortcut labels
 *  @type array
 */
function createTimeShortcuts(scs)
{
	var result = new Array(scs.length);
	for (var i = 0; i < scs.length; i++)
	{
		var d = scs[i];
		var sc = '['+ d + '] Last ';
		var c = d.charAt(d.length - 1);
		var q = d.substring(1, d.length - 1);
		var pl = (q != 1);
		if (pl)
			sc = sc + q + ' ';
		switch(c)
		{
			case 'i':
				sc = sc + 'minute' + (pl ? 's' : '');
				break;
			case 'h':
				sc = sc + 'hour' + (pl ? 's' : '');
				break;
			case 'd':
				sc = sc + 'day' + (pl ? 's' : '');
				break;
			case 'w':
				sc = sc + 'week' + (pl ? 's' : '');
				break;
			case 'm':
				sc = sc + 'month' + (pl ? 's' : '');
				break;
			case 'y':
				sc = sc + 'year' + (pl ? 's' : '');
				break;
		}
		result[i] = sc;
	}
	return result;
}

/**
 *  Populates the shortcut panel that you can get by clicking the clock icon in the 
 *  time entry panel on the right hand side of the text menus.
 *  Remove old ones, add new ones
 *
 *  @param {array} shortcuts Example of array of strings: "-1i", "-2i", "-5i", "-10i", "-20i", "-30i", "-1h"
 */
function populateTimeShortcuts(shortcuts)
{
	var scs = createTimeShortcuts(shortcuts);
	var p = document.getElementById('timeShortcutPanel');
	for (var i = 0; i < p.childNodes.length; i++)
	{
		if (p.childNodes[i].tagName == 'P')
		{
			p.removeChild(p.childNodes[i]);
			i--;
		}
	}
	for (var i = 0; i < scs.length; i++)
	{
		var item = document.createElement('P');
		item.appendChild(document.createTextNode(scs[i]));
		p.appendChild(item);
	}
}

/** 
 *	Clicking on a time shortcut drops that shortcut into the "Start Time" entry
 *
 *  @param {event object} event for example: mouse click
 */
function timeShortcutClick(event)
{
	var t = /\[(.*)\]/;
	var target = getTarget(event);
	if (target.tagName && target.tagName.toUpperCase() == 'P')
	{
		var sc = target.firstChild.nodeValue;
		var re = sc.match(t);
		document.getElementById("startTime").value = re[1];
		document.getElementById('endTime').value = 'Now';
		toggleTimeShortcutPanel();
	}
}
/**
 *  clicking the clock icon to the left of 'Start Time' will bring up the panel 
 *  that displays shortcut hints, ie: [-1w] Last week.
 *  If it's already up, running this function will close the panel
 *
 *  @param {event object} event for example: mouse click
 */
function toggleTimeShortcutPanel(event)
{
	var p = document.getElementById('timeShortcutPanel');
	if (p.style.display == "block")
		p.style.display = "none";
	else
	{
		var xo = 0;
		var yo = 0;
		var el = document.getElementById("timeShortcutButton");
		do 
		{
			xo += el.offsetLeft;
			yo += el.offsetTop;
		}
		while ((el = el.offsetParent));
		p.style.display = "block";
		p.style.top = (yo - 4) + "px";
		p.style.left = (xo - 155) + "px";
	}
}

/**
 *  Check the current geographic filter interface element to find out the active geo filter.
 *  For example "Alaska": return appropriate latitude and longitude to define the area. This 
 *  is parsed out of the text array 'value' stored in the popup menu, 
 *  ie: [172, -129.9, 51, 72]
 *  These values originate from AVO/filters.txt
 *  
 *  @return array of numbers
 *  @type array	
 */
function getGeoFilter()
{
	var gf = document.getElementById("geoFilter");
	var wesn = gf[gf.selectedIndex].value;
	if (wesn == "[none]")
		return null;
	else
	{
		wesn = wesn.split(",");
		for (var i = 0; i < 4; i++)
			wesn[i] = wesn[i] * 1;
		return wesn;
	}
}

/**
 *  Create an empty menu function to be filled out later
 */
function Menu()
{}

Menu.prototype.allowChannelMap = false;

/**
	Initialize the time shortcuts array
 */
Menu.prototype.timeShortcuts = new Array("-1h");

/**
 	Sets up a function to take some parameters.
 */
Menu.prototype.acceptMapClick = function(target, mx, my, lon, lat)
{}

/**
	Sets the start and end times in the main menu to start and 
	end times for the area clicked on (assuming X values are over time)
	The first click sets the start time. The second click sets the end time.
	(Then you can submit to zoom in on your new time range.)
 */
Menu.prototype.acceptTYClick = function(target, mx, my, gx, gy)
{
	if (lastTimeClick++ % 2 == 0)
		document.getElementById("startTime").value = buildTimeString(gx);
	else
		document.getElementById("endTime").value = buildTimeString(gx);
}
/**
 	Sets up a function to take some parameters.
 */
Menu.prototype.acceptXYClick = function(target, mx, my, gx, gy)
{}

/**
	Generate the name for calling a form element, and return the form element
 */
Menu.prototype.getForm = function()
{
	return document.getElementById(this.id + '_' + this.formName);
}

/**
	Initiate populateSelectors to populate the channel names into the menu
 */ 
Menu.prototype.loadChannels = function()
{
	if (this.selector && this.formName)
		populateSelectors(this);
		//populateSelectors(this.id, this.formName, this.selector);
}

/** 
	Initialize the sub-menus once a menu selection has been made. The sub-menus
	happen in the "box"
 */
Menu.prototype.initialize = function()
{
	activateBox(this.boxName, this.id);
	this.loadChannels();
}

Menu.prototype.presubmit = function() { return true; }

/**
	This is the main "submit" button. Some error checking is sone on channels before
	the asynchronous xml request is made.
 */
Menu.prototype.submit = function()
{
	var f = this.getForm();
	var t = getTimes();
	if (t == null)
		return;
		
	if (this.selector)
	{
		var sel = f[this.selector];
		if (sel.selectedIndex == -1)
		{
			alert("You must select a channel.");
			return;
		}
		if (sel.selectedIndex >= 0 && sel[sel.selectedIndex].text.indexOf('[') != -1)
		{
			alert("Illegal channel.");
			return;
		}
	}
	var pr = new PlotRequest();
	var pc = pr.createComponent(this.id, t.st, t.et);
	pc.setFromForm(f);
	if (this.presubmit(pr, pc))
		loadXML(this.id + " plot", pr.getURL());
}

/**
	Set and apply the geographicial filter for the selection in the first sub-menu.
	and for any map to be drawn
 */
Menu.prototype.filterChanged = function()
{
	if (this.selector && this.getForm().options)
	{
		var wesn = getGeoFilter();
		var f = this.getForm();
		var sel = f[this.selector];
		while (sel.hasChildNodes())
			sel.removeChild(sel.firstChild);
		for (var i = 0; i < f.options.length; i++)
		{
			var pt = f.options[i].value.split(":");
			if (wesn == null || (wesn[0] < pt[1] * 1 && wesn[1] > pt[1] * 1 &&
				wesn[2] < pt[2] * 1 && wesn[3] > pt[2] * 1))
			{
				sel.appendChild(f.options[i]);
			}
		}
		if (sel.options.length == 0)
		{
			var opt = document.createElement('option');
			opt.appendChild(document.createTextNode("[filter: no result]"));
			sel.appendChild(opt);
		}
	}
}

/** 
	Run filterChanged based on the new menu chosen.
 */ 
Menu.prototype.menuFocused = function()
{
	this.filterChanged();
}
