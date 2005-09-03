// $Id: menu.js,v 1.1 2005-09-03 19:18:35 dcervelli Exp $

var lastDiv = null;
var currentMenu = null;
function setCurrentMenu(menu)
{
	currentMenu = menu;
	populateTimeShortcuts(menu.timeShortcuts);
	menu.menuFocused();
}

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
	setAdmin(getXMLField(xml, "administrator"), getXMLField(xml, "administrator-email"));
	document.getElementById('version').appendChild(document.createTextNode("Valve " + getXMLField(xml, "version")));
}

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

function loadDataSources()
{
	loadFilters();
	var	url = "valve3.jsp?a=menu";
	loadXML("main menu", url);
}

//function populateSelectors(id, formId, selectId)
function populateSelectors(menu)
{
	var f = document.getElementById(menu.id + '_' + menu.formName);
	var select = f.elements[menu.selector];
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
				//opt.value = ss[0];
				opt.value = val;
				opt.appendChild(document.createTextNode(ss[3]));
				select.appendChild(opt);
				opts[i] = opt;
			}
			f.options = opts;
			menu.filterChanged();
		});
}

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

function doMouseOver(e)
{
	var target = getTarget(e);
	if (target.className == "selected")
		wasSelected = true;
	target.className = "hover";
}

function doMouseOut(e)
{
	var target = getTarget(e);
	if (wasSelected)
		target.className = "selected";
	else
		target.className = "";
	wasSelected = false;
}

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

function timeShortcutClick(event)
{
	var t = /\[(.*)\]/;
	var target = getTarget(event);
	if (target.tagName && target.tagName.toUpperCase() == 'P')
	{
		var sc = target.firstChild.nodeValue;
		var re = sc.match(t);
		document.getElementById("startTime").value = re[1];
		toggleTimeShortcutPanel();
	}
}

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

function Menu()
{}

Menu.prototype.allowChannelMap = false;

Menu.prototype.timeShortcuts = new Array("-1h");

Menu.prototype.acceptMapClick = function(target, mx, my, lon, lat)
{}

Menu.prototype.acceptTYClick = function(target, mx, my, gx, gy)
{
	if (lastTimeClick++ % 2 == 0)
		document.getElementById("startTime").value = buildTimeString(gx);
	else
		document.getElementById("endTime").value = buildTimeString(gx);
}

Menu.prototype.acceptXYClick = function(target, mx, my, gx, gy)
{}

Menu.prototype.getForm = function()
{
	return document.getElementById(this.id + '_' + this.formName);
}

Menu.prototype.loadChannels = function()
{
	if (this.selector && this.formName)
		populateSelectors(this);
		//populateSelectors(this.id, this.formName, this.selector);
}

Menu.prototype.initialize = function()
{
	activateBox(this.boxName, this.id);
	this.loadChannels();
}

Menu.prototype.presubmit = function() { return true; }

Menu.prototype.submit = function()
{
	var f = this.getForm();
	var t = getTimes();
	if (t == null)
		return;
		
	if (this.selector)
	{
		var sel = f[this.selector];
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

Menu.prototype.filterChanged = function()
{
	if (this.selector && this.getForm().options)
	{
		var wesn = getGeoFilter();
		var f = this.getForm();
		var sel = f[this.selector];
		sel.options.length = 0;
		for (var i = 0; i < f.options.length; i++)
		{
			var pt = f.options[i].value.split(":");
			if (wesn == null || (wesn[0] < pt[1] * 1 && wesn[1] > pt[1] * 1 &&
				wesn[2] < pt[2] * 1 && wesn[3] > pt[2] * 1))
			{
				sel.options[sel.options.length] = f.options[i];
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

Menu.prototype.menuFocused = function()
{
	this.filterChanged();
}
