/** 
 * @fileoverview Functions in menu.js have to do with the text-based menus at the top of the Valve screen 
 * @author Dan Cervelli
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
function loadMenu(id) {
	
	if (lastDiv)
		lastDiv.style.display = 'none';
		
	if (menus[id].div) {
		menus[id].div.style.display = 'block';
		lastDiv = menus[id].div;
		setCurrentMenu(menus[id]);
		var selector_lt = document.getElementById("selector:lt");
		if(selector_lt){
			for (var i = 0; i < selector_lt.options.length; i++)  {
				if(selector_lt.options[i].value==menus[id].lineType){
					selector_lt.options[i].selected=true;
					break;
				}
			}
		}	
		var selector_bt = document.getElementById("dmo_debias_pick");
		if(selector_bt){
			for (var i = 0; i < selector_bt.options.length; i++)  {
				if(selector_bt.options[i].value==menus[id].biasType){
					selector_bt.options[i].selected=true;
					break;
				}
			}
		}	
		return;
	}
	var url = "menu/" + menus[id].file + ".html";
	loadXML(menus[id].file + " javascript", url, 
		function(req) {	
			menus[id].html	= req.responseText;
			var div			= document.createElement('div');
			div.innerHTML	= menus[id].html;
			document.getElementById('openPanel').appendChild(div);
			menus[id].div	= div;
			lastDiv			= div;
			loadXML(menus[id].file + " javascript", "menu/" + menus[id].file + ".js", function(req) {
				if (req.readyState == 4 && req.status == 200) {
					menus[id].js = req.responseText;
					eval(menus[id].js);
					eval('create_' + menus[id].file + '(menus[id], id)');
					menus[id].initialize();
					setCurrentMenu(menus[id]);
					var plotSeparately			= document.createElement('input');
					plotSeparately.name = "plotSeparately";
					plotSeparately.type = "hidden";
					plotSeparately.value = menus[id].plotSeparately;
					document.getElementById(id + '_' + menus[id].formName).appendChild(plotSeparately);
				}
			});
			var selector_lt = document.getElementById("selector:lt");
			if(selector_lt){
				for (var i = 0; i < selector_lt.options.length; i++)  {
					if(selector_lt.options[i].value==menus[id].lineType){
						selector_lt.options[i].selected=true;
						break;
					}
				}
			}	
			var selector_bt = document.getElementById("dmo_debias_pick");
			if(selector_bt){
				for (var i = 0; i < selector_bt.options.length; i++)  {
					if(selector_bt.options[i].value==menus[id].biasType){
						selector_bt.options[i].selected=true;
						break;
					}
				}
			}	
	});
}

var menus = new Array(10);

/**
 *  Set up the initial menu, populate data sources - current data, historical data, 
 *  geographic filter, start/end time, administrator email link, etc.
 *  
 *  @param {text} xml block of xml text
 */
function handleMenu(xml) {
	
	var sections	= xml.getElementsByTagName("section");
	var vp			= document.getElementById('dataPanel');
	
	for (var i = 0; i < sections.length; i++) {
		//var icon = sections[i].getElementsByTagName("icon")[0].firstChild.data;
		var name = sections[i].getElementsByTagName("name")[0].firstChild.data;
	
		var t		= document.getElementById('listItemTemplate').cloneNode(true);
		t.id		= "section" + i;
		var header	= t.getElementsByTagName('h1')[0];
		header.childNodes[1].nodeValue = ' ' + name;
		var img		= t.getElementsByTagName('img')[0];
		addListener(img, 'click', function(e) { toggle(e); }, false);

		var items	= sections[i].getElementsByTagName("menuitem");
		var ip		= t.getElementsByTagName('ul')[0];
		for (var j = 0; j < items.length; j++) {
			
			var menuid			= items[j].getElementsByTagName("menuid")[0].firstChild.data;
			var name			= items[j].getElementsByTagName("name")[0].firstChild.data;
			var file			= items[j].getElementsByTagName("file")[0].firstChild.data;
			var lineType		= items[j].getElementsByTagName("lineType")[0].firstChild.data;
			var plotSeparately	= items[j].getElementsByTagName("plotSeparately")[0].firstChild.data;
			var biasType		= items[j].getElementsByTagName("biasType")[0].firstChild.data;
			// TODO: figure out why this breaks the parser !!
			// var timeShortcuts	= items[j].getElementsByTagName("timeShortcuts")[0].firstChild.data;
			var timeShortcuts	= "";
			
			var m			= new Menu();
			m.id			= menuid;
			m.file			= file;
			m.timeShortcuts	= timeShortcuts.split(",");
			m.lineType		= lineType;
			m.plotSeparately = plotSeparately;
			m.biasType      = biasType;
			menus[menuid]	= m;
			var li			= document.createElement('li');
			li.id			= menuid;
			li.appendChild(document.createTextNode(name));
			addListener(li, 'click', function(e) { doSelect(e); }, false);
			addListener(li, 'mouseover', function(e) { doMouseOver(e); }, false);
			addListener(li, 'mouseout', function(e) { doMouseOut(e); }, false);
			
			ip.appendChild(li);
		}
		vp.getElementsByTagName('ul')[0].appendChild(t);

	    if ( sections[i].getElementsByTagName("expanded")[0].firstChild.data == "true" ) {
			// It would be better to call toggle, but then we'd need to fake an event
			var subMenu = t.getElementsByTagName("ul")[0];
			var img = t.getElementsByTagName("img")[0];
			subMenu.className = "subMenu";
			img.src = "images/minus.png";
	    }
	}
	
	var inst = xml.getElementsByTagName("title")[0].firstChild.data;
	document.title = inst;
	
	document.getElementById('appTitle').appendChild(document.createTextNode(inst));
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
function loadFilters() {
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
function loadDataSources() {
	loadFilters();
	var	url = "valve3.jsp?a=menu";
	loadXML("main menu", url);
}

/** 
 *	Make an AJAX call via jsp to return the values for the Channels menu
 *
 *  Index	New		Old
 *  [0] 	cid		cid
 *  [1]		code	lon
 *  [2]		name	lat
 *  [3]		lon 	code
 *  [4]		lat		name
 *  [5]		height	ctid
 *  [6]     ctid
 *  
 *  @param {menu object} menu 
 */
function populateChannels(menu) {
	var form	= document.getElementById(menu.id + '_' + menu.formName);
	var select	= form.elements[menu.selector];	
	var url 	= "valve3.jsp?a=data&src=" + menu.id + "&da=channels";
	
	loadXML(menu.id + " channels", url, 
		function(req) {
			var xml		= req.responseXML;
			var ch		= xml.getElementsByTagName('list-item');
			var opts	= new Array(ch.length);
			// var temp	= (new XMLSerializer()).serializeToString(xml);
			
			for (var i = 0; i < ch.length; i++) {
				var val	= ch[i].firstChild.nodeValue;
				var ss	= val.split(':');
				var opt	= document.createElement('option');
				
				// assign the value the full colon separated string of channel information
				opt.value = val;
				
				opt.appendChild(document.createTextNode(ss[1].replace(/\$/g,' ')));
				select.appendChild(opt);
				opts[i] = opt;
			}
			
			// save the complete list
			menu.allChannels = opts;
			
			// refresh the list if there is a geographic filter already selected
			menu.filterChanged();
		});
}

/**
 * Make an AJAX call via jsp to return the values for the Channel Types menu
 * The request to valve for channel types returns a 2D array. Each array element contains a
 * sub-array for each channel type.
 * [0] ctid
 * [1] name
 * 
 * @param {menu object} menu
 */
function populateChannelTypes(menu) {
	var form	= document.getElementById(menu.id + '_' + menu.formName);
	var select	= form.elements[menu.selector];
	var url		= "valve3.jsp?a=data&src=" + menu.id + "&da=channelTypes";
	
	loadXML(menu.id + " channel types", url,
		function(req) {	
			var xml		= req.responseXML;
			var ch		= xml.getElementsByTagName('list-item');
			var opts	= new Array(ch.length + 1);
			// var temp	= (new XMLSerializer()).serializeToString(xml);
			
			// create the default channel type of '[none]'
			var opt		= document.createElement('option');
			opt.value	= "[none]";
			opt.appendChild(document.createTextNode("[none]"));
			select.appendChild(opt);
			
			for (var i = 0; i < ch.length; i++) {
				var val	= ch[i].firstChild.nodeValue;
				var ss	= val.split(':');
				var opt	= document.createElement('option');					
				opt.value = ss[0];				
				opt.appendChild(document.createTextNode(ss[1]));
				select.appendChild(opt);
				opts[i] = opt;
			}
			
			// save the complete list
			menu.allChannelTypes = opts;
			
			addListener(form, 'change', function(e) { if (currentMenu) currentMenu.filterChanged(); }, false);
		});
}

/**
 * Make an AJAX call via jsp to return the values for a select options list
 * The request to valve for select options returns a 2D array. Each array element contains a
 * sub-array for each select option.
 * [0] idx
 * [1] code
 * [2] name
 * 
 * @param {menu object} menu
 */
function populateSelectOptions(menu, selectName) {
	var form	= document.getElementById(menu.id + '_' + menu.formName);
	var select	= form.elements[menu.selector];
	var url		= "valve3.jsp?a=data&src=" + menu.id + "&da=" + selectName;
	
	loadXML(menu.id + " select options", url,
		function(req) {	
			var xml		= req.responseXML;
			var so		= xml.getElementsByTagName('list-item');
			var opts	= new Array(so.length + 1);
			// var temp	= (new XMLSerializer()).serializeToString(xml);
			
			// create the default channel type of '[none]'
			var opt		= document.createElement('option');
			opt.value	= "";
			opt.appendChild(document.createTextNode("[none]"));
			select.appendChild(opt);
			
			for (var i = 0; i < so.length; i++) {
				var val	= so[i].firstChild.nodeValue;
				var ss	= val.split(':');
				var opt	= document.createElement('option');					
				opt.value = ss[1];				
				opt.appendChild(document.createTextNode(ss[2]));
				select.appendChild(opt);
				opts[i] = opt;
			}
		});
}

/**
 * Make an AJAX call via jsp to return the values for the columns menu
 * The request to valve for columns returns a 2D array. Each array element contains a
 * sub-array for each column.
 * 
 * @param {menu object} menu
 */
function populateGenericColumns(menu) {
	var mainColDiv	= document.getElementById(menu.id + "_columns");
	var url			= "valve3.jsp?a=data&src=" + menu.id + "&da=columns";
	
	loadXML(menu.id + " columns", url, 
		function(req) {
			var xml		= req.responseXML;
			var cols	= xml.getElementsByTagName('list-item');
			// var temp	= (new XMLSerializer()).serializeToString(xml);
			
			var colDiv = document.createElement('div');
			colDiv.className = 'mlr4';
			var uniDiv = document.createElement('div');
			uniDiv.className = 'hide';
			var detDiv = document.createElement('div');
			detDiv.className = 'fr mlr4';
			var norDiv = document.createElement('div');
			norDiv.className = 'fr mlr4';

			colDiv.id = menu.id + 'colNames';
			uniDiv.id = menu.id + 'colUnits';
			
			var p = document.createElement('p');
			var l = document.createTextNode('Name');
			p.appendChild(l);
			colDiv.appendChild(p);
			
			//var p = document.createElement('p');
			//var l = document.createTextNode('Detrend');
			//p.appendChild(l);
			//detDiv.appendChild(p);
			
			//var p = document.createElement('p');
			//var l = document.createTextNode('Remove Bias');
			//p.appendChild(l);
			//norDiv.appendChild(p);
			
			for (var i = 0; i < cols.length; i++) {
				
				// split up the column entry into it's elements
				var col		= cols[i].firstChild.data.split(":");
				
				// build the column checkbox
				var p		= document.createElement('p');
				
				var el		= document.createElement('input');
				el.type		= 'checkbox';
				el.id		= menu.id + "_" + col[1];
				if (col[4] == "T") { el.checked = "checked"; }
				el.name		= col[1];				
				p.appendChild(el);
				
				var el		= document.createElement('label');
				el.setAttribute("for", menu.id + "_" + col[1]);
				var tn		= document.createTextNode(" " + col[2] + " (" + col[3] + ")");
				el.appendChild(tn);
				p.appendChild(el);
				
				colDiv.appendChild(p);
				
				// build the units list
				var p		= document.createElement('p');
				var l		= document.createTextNode(col[3]);
				p.appendChild(l);
				uniDiv.appendChild(p);
				
				// build the detrend checkbox
				/*
				var p		= document.createElement('p');
				p.className	= 'center';
				var el		= document.createElement('input');
				el.type		= 'checkbox';
				el.id		= menu.id + "_d_" + col[1];
				el.name		= "d_" + col[1];
				p.appendChild(el);
				detDiv.appendChild(p);
				*/
				
				// build the normalize checkbox
				/*
				var p		= document.createElement('p');
				p.className	= 'center';
				var el		= document.createElement('input');
				el.type		= 'checkbox';
				el.id		= menu.id + "_n_" + col[1];
				el.name		= "n_" + col[1];
				p.appendChild(el);
				norDiv.appendChild(p);
				*/
			}
			
			mainColDiv.appendChild(detDiv);
			mainColDiv.appendChild(norDiv);
			mainColDiv.appendChild(colDiv);
			mainColDiv.appendChild(uniDiv);
		}
	);

	/* loadXML(menu.id + " datamanip", url, 
		function(req) {
			var xml		= req.responseXML;
			var cols	= xml.getElementsByTagName('list-item');
			// var temp	= (new XMLSerializer()).serializeToString(xml);
			
			var argsDiv = document.createElement('div');
			argsDiv.className = 'fr mlr4';
			var lblsDiv = document.createElement('div');
			lblsDiv.className = 'fr mlr4';
			var menuDiv = document.createElement('div');
			menuDiv.className = 'mlr4';

			argsDiv.id = menu.id + 'dmo_args';
			lblsDiv.id = menu.id + 'dmo_arglbls';
			menuDiv.id = menu.id + 'dmo_menu';
			
			var p = document.createElement('p');
			var l = document.createTextNode('Name');
			p.appendChild(l);
			colDiv.appendChild(p);
			
			//var p = document.createElement('p');
			//var l = document.createTextNode('Detrend');
			//p.appendChild(l);
			//detDiv.appendChild(p);
			
			//var p = document.createElement('p');
			//var l = document.createTextNode('Remove Bias');
			//p.appendChild(l);
			//norDiv.appendChild(p);
			
			for (var i = 0; i < cols.length; i++) {
				
				// split up the column entry into it's elements
				var col		= cols[i].firstChild.data.split(":");
				
				// build the column checkbox
				var p		= document.createElement('p');
				
				var el		= document.createElement('input');
				el.type		= 'checkbox';
				el.id		= menu.id + "_" + col[1];
				if (col[4] == "T") { el.checked = "checked"; }
				el.name		= col[1];				
				p.appendChild(el);
				
				var el		= document.createElement('label');
				el.setAttribute("for", menu.id + "_" + col[1]);
				var tn		= document.createTextNode(" " + col[2] + " (" + col[3] + ")");
				el.appendChild(tn);
				p.appendChild(el);
				
				colDiv.appendChild(p);
				
				// build the units list
				var p		= document.createElement('p');
				var l		= document.createTextNode(col[3]);
				p.appendChild(l);
				uniDiv.appendChild(p);
				
				// build the detrend checkbox
				//
				//var p		= document.createElement('p');
				//p.className	= 'center';
				//var el		= document.createElement('input');
				//el.type		= 'checkbox';
				//el.id		= menu.id + "_d_" + col[1];
				//el.name		= "d_" + col[1];
				//p.appendChild(el);
				//detDiv.appendChild(p);
				
				// build the normalize checkbox
				//
				//var p		= document.createElement('p');
				//p.className	= 'center';
				//var el		= document.createElement('input');
				//el.type		= 'checkbox';
				//el.id		= menu.id + "_n_" + col[1];
				//el.name		= "n_" + col[1];
				//p.appendChild(el);
				//norDiv.appendChild(p);
				
			}
			
			mainColDiv.appendChild(detDiv);
			mainColDiv.appendChild(norDiv);
			mainColDiv.appendChild(colDiv);
			mainColDiv.appendChild(uniDiv);
		}
	); */
}

function populateTiltColumns(menu) {
	var mainColDiv	= document.getElementById(menu.id + "_columns");
	var url			= "valve3.jsp?a=data&src=" + menu.id + "&da=columns";
	
	loadXML(menu.id + " columns", url, 
		function(req) {
			var xml		= req.responseXML;
			var cols	= xml.getElementsByTagName('list-item');
			// var temp	= (new XMLSerializer()).serializeToString(xml);
			
			var colDiv = document.createElement('div');
			colDiv.className = 'mlr4';
			var uniDiv = document.createElement('div');
			uniDiv.className = 'hide';
			//var detDiv = document.createElement('div');
			//detDiv.className = 'fr mlr4';

			colDiv.id = menu.id + 'colNames';
			uniDiv.id = menu.id + 'colUnits';
			
			var p = document.createElement('p');
			var l = document.createTextNode('Name');
			p.appendChild(l);
			colDiv.appendChild(p);
			
			//p = document.createElement('p');
			//l = document.createTextNode('DMO');
			//p.appendChild(l);
			//detDiv.appendChild(p);
			
			for (var i = 0; i < cols.length; i++) {
				
				// split up the column entry into it's elements
				var col		= cols[i].firstChild.data.split(":");
				
				// build the column checkbox
				var p		= document.createElement('p');
				
				var el		= document.createElement('input');
				el.type		= 'checkbox';
				el.id		= menu.id + "_" + col[1];
				if (col[4] == "T") { el.checked = "checked"; }
				el.name		= col[1];
				p.appendChild(el);
								
				var el		= document.createElement('label');
				el.setAttribute("for", menu.id + "_" + col[1]);
				var tn		= document.createTextNode(" " + col[2] + ": " + col[3] + "");
				el.appendChild(tn);
				p.appendChild(el);

				colDiv.appendChild(p);
				
				// build the units list
				var p		= document.createElement('p');
				var l		= document.createTextNode(col[3]);
				p.appendChild(l);
				uniDiv.appendChild(p);
				
				// build the detrend checkbox
				//var p		= document.createElement('p');
				//p.className	= 'center';
				//var el		= document.createElement('label');
				//el.appendChild( document.createTextNode(col[5] == "F" ? "No" : "Yes"));
				//p.appendChild(el);
				//detDiv.appendChild(p);
			}
			
			//mainColDiv.appendChild(detDiv);
			mainColDiv.appendChild(colDiv);
			mainColDiv.appendChild(uniDiv);
		}
	);
}

function populateGPSColumns(menu) {
	var mainColDiv	= document.getElementById(menu.id + "_columns");
	var url			= "valve3.jsp?a=data&src=" + menu.id + "&da=columns";
	
	loadXML(menu.id + " columns", url, 
		function(req) {
			var xml		= req.responseXML;
			var cols	= xml.getElementsByTagName('list-item');
			// var temp	= (new XMLSerializer()).serializeToString(xml);
			
			var colDiv = document.createElement('div');
			colDiv.className = 'mlr4';
			var uniDiv = document.createElement('div');
			uniDiv.className = 'hide';

			colDiv.id = menu.id + 'colNames';
			uniDiv.id = menu.id + 'colUnits';
			
			var p = document.createElement('p');
			var l = document.createTextNode('Name');
			p.appendChild(l);
			colDiv.appendChild(p);
			
			for (var i = 0; i < cols.length; i++) {
				
				// split up the column entry into it's elements
				var col		= cols[i].firstChild.data.split(":");
				
				// build the column checkbox
				var p		= document.createElement('p');

				var el		= document.createElement('input');
				el.type		= 'checkbox';
				el.id		= menu.id + "_" + col[1];
				if (col[4] == "T") { el.checked = "checked"; }
				el.name		= col[1];
				p.appendChild(el);				
				
				var el		= document.createElement('label');
				el.setAttribute("for", menu.id + "_" + col[1]);
				var tn		= document.createTextNode(" " + col[2] + " (" + col[3] + ")");
				el.appendChild(tn);
				p.appendChild(el);
				
				colDiv.appendChild(p);
				
				// build the units list
				var p		= document.createElement('p');
				var l		= document.createTextNode(col[3]);
				p.appendChild(l);
				uniDiv.appendChild(p);
			}

			mainColDiv.appendChild(colDiv);
			mainColDiv.appendChild(uniDiv);
		}
	);
}

function countSelectedColumns(menu) {
	var selectedColumns	= 0;
	var namesElement	= document.getElementById(menu.id + "colNames");
	var namesCollection	= namesElement.getElementsByTagName('INPUT');
	for (var i = 0; i < namesCollection.length; i++) {
		if (namesCollection[i].checked) {
			selectedColumns++;
		}
	}
	return selectedColumns;
}

function validateColumns(menu) {

	var unitsArray	= new Array();
	var count		= 0;
	var in_array	= false;
	
	var unitsElement	= document.getElementById(menu.id + "colUnits");
	var namesElement	= document.getElementById(menu.id + "colNames");
	var unitsCollection	= unitsElement.getElementsByTagName('P');
	var namesCollection	= namesElement.getElementsByTagName('INPUT');

	for (var i = 0; i < namesCollection.length; i++) {
		if (namesCollection[i].checked) {
			in_array = false;
			for (var j = 0; j < count; j++) {
				if (unitsCollection[i].innerHTML == unitsArray[j]) {
					in_array = true;
					continue;
				}
			}
			if (!in_array) {
				unitsArray[count] = unitsCollection[i].innerHTML;
				count++;
			}
		}
	}		
	
	if (unitsArray.length == 0) {
		alert("You must select at least one component.");
		return false;
		
	} else if (unitsArray.length > 2) {
		alert("You can select at most two units.");
		return false;
	}
	return true;
}

/**
 * Make an AJAX call via jsp to return the values for the Ranks menu
 * The request to valve for ranks returns an 2D array. Each array element contains a
 * sub-array for each rank.
 * [0] rid
 * [1] name
 * [2] rank
 * [3] default (0 or 1)
 * 
 * @param {menu object} menu
 */
function populateRanks(menu) {
	var form	= document.getElementById(menu.id + '_' + menu.formName);
	var select	= form.elements[menu.selector];
	var url		= "valve3.jsp?a=data&src=" + menu.id + "&da=ranks";
	
	loadXML(menu.id + " ranks", url,
		function(req) {
			var xml		= req.responseXML;
			var ch		= xml.getElementsByTagName('list-item');
			var opts	= new Array(ch.length + 1);
			// var temp	= (new XMLSerializer()).serializeToString(xml);
			
			for (var i = 0; i < ch.length; i++) {
				var val	= ch[i].firstChild.nodeValue;
				var ss	= val.split(':');
				var opt	= document.createElement('option');					
				opt.value = ss[0];				
				opt.appendChild(document.createTextNode(ss[1] + " (" + ss[2] + ")"));
				if (ss[3] == "1") { opt.selected = true; }
				select.appendChild(opt);
				opts[i] = opt;
			}
			
			// add in the option for best possible rank
			var opt		= document.createElement('option');
			opt.value	= "0";
			opt.appendChild(document.createTextNode("Best Possible Rank"));
			select.appendChild(opt);
			opts[i]		= opt;
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
	if (subMenu.className == "subMenu")
	{
		subMenu.className = "hiddenSubMenu";
		img.src = "images/plus.png";
	}
	else
	{
		subMenu.className = "subMenu";
		img.src = "images/minus.png";
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
function populateTimeShortcuts(shortcuts) {
	
	var scs	= createTimeShortcuts(shortcuts);
	var p	= document.getElementById('timeShortcutPanel');
	
	for (var i = 0; i < p.childNodes.length; i++) {
		if (p.childNodes[i].tagName == 'P') {
			p.removeChild(p.childNodes[i]);
			i--;
		}
	}
	
	for (var i = 0; i < scs.length; i++) {
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
	var gf		= document.getElementById("geoFilter");
	var wesn	= gf[gf.selectedIndex].value;
	if (wesn == "[none]") {
		return null;
	} else {
		wesn = wesn.split(",");
		for (var i = 0; i < 4; i++)
			wesn[i] = wesn[i] * 1;
		return wesn;
	}
}

/**
 * Looks at the current value of the channel type filter on the screen and gets the value
 * 
 * @return ctid
 */
function getChannelTypeFilter(form) {
	var ctid	= null;
	var select	= form.elements["selector:ct"];
	if (select) {
		ctid 	= select[select.selectedIndex].value;
		if (ctid == "[none]") {
			ctid = null;
		}
	}
	return ctid;
}

/**
 *  Create an empty menu function to be filled out later
 */
function Menu()
{}

Menu.prototype.allowChannelMap = false;

/**
	Initialize the time shortcuts array (for all menus)
 */
Menu.prototype.timeShortcuts = new Array("-1h","-1d","-1w","-1m","-1y");

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
	Also sets markers for clicked times at top of plot (green=start,red=end).
 */
Menu.prototype.acceptTYClick = function(target, mx, my, gx, gy)
{
	/* Clicks on minimized plots don't translate properly, so we'll ignore them */
	if ( target.width < target.fullWidth )
		return;
		
	var scroll = getScroll();
	var mark;			/* marker for this click */
	var otherMark;		/* the other marker */

	/* Toggle between green & red markers */
	lastTimeClick = 1 - lastTimeClick;
	if (lastTimeClick == 1) {
		mark = document.getElementById("greenMark")
		otherMark = document.getElementById("redMark")
	} else {
		mark = document.getElementById("redMark")
		otherMark = document.getElementById("greenMark")
	}
	
	if ( mark.parentNode != target.parentNode ) {
		/* If click isn't in marker's parent, user clicked on a different plot.
			Force click to be treated as start time & hide end marker */
		var oldParent = mark.parentNode;
		mark = oldParent.removeChild(mark);
		otherMark = oldParent.removeChild(otherMark);
		target.parentNode.appendChild(mark);
		target.parentNode.appendChild(otherMark);
		if ( lastTimeClick == 0 ) {
			var t = mark;
			mark = otherMark;
			otherMark = t;
			lastTimeClick = 1;
		}
		otherMark.style.visibility = "hidden";
	}
	
	/* Set the appropriate time field */
	if (lastTimeClick == 1) {
		document.getElementById("startTime").value	= buildTimeString(gx);
	} else {
		document.getElementById("endTime").value	= buildTimeString(gx);
	}
	
	/* Make mark visible, and align its horizontal position w/ the click */
	mark.style.visibility = "visible";
	var leftSide = 0;
	var imgs = mark.parentNode.getElementsByTagName("img");
	var i;
	for ( i = 0; imgs[i].className != "pointer"; i++ );
	/* leftSide = imgs[i].x; */
	i = imgs[i];
	while ( i != null ) {
		leftSide = leftSide + i.offsetLeft;
		i = i.offsetParent;
	}
	mark.style.left=(scroll[0] + mx - leftSide - 5) + "px";
}

/**
 	Sets up a function to take some parameters.
 */
Menu.prototype.acceptXYClick = function(target, mx, my, gx, gy) {}

/**
	Generate the name for calling a form element, and return the form element
 */
Menu.prototype.getForm = function() {
	return document.getElementById(this.id + '_' + this.formName);
}

/**
	Initiate populateChannels to populate the channel names into the menu
 */ 
Menu.prototype.loadChannels = function() {
	this.selector	= "selector:ch";
	var form		= this.getForm();
	var select		= form.elements[this.selector];
	if (select) {
		populateChannels(this);
	}
}

/**
	Initiate populateChannels to populate the channel names into the menu
 */ 
Menu.prototype.loadChannelTypes = function() {
	this.selector	= "selector:ct";
	var form		= this.getForm();
	var select		= form.elements[this.selector];
	if (select) {
		populateChannelTypes(this);
	}
}

/**
	Initiate populateColumns to populate the column names and options into the menu
 */ 
Menu.prototype.loadColumns = function() {
	var colDiv	= document.getElementById(this.id + "_columns");
	if (colDiv) {
		populateColumns(this);
	}
}

/**
 * Initiate populateRanks to populate the ranks into the menu
 */
Menu.prototype.loadRanks = function() {
	this.selector	= "selector:rk";
	var form		= this.getForm();
	var select		= form.elements[this.selector];
	if (select) {
		populateRanks(this);
	}
}

/** 
	Initialize the sub-menus once a menu selection has been made. The sub-menus
	happen in the "box"
 */
Menu.prototype.initialize = function() {
	activateBox(this.boxName, this.id);
	this.loadChannelTypes();
	this.loadChannels();
	this.loadRanks();
	// this.loadColumns();
}

Menu.prototype.presubmit = function() {
	var colDiv	= document.getElementById(this.id + "_columns");
	if (colDiv && !validateColumns(this)) {
		return false;
	}
	var decimationType = document.getElementById(this.id + "_selector:ds");
	if(decimationType!=null && decimationType.selectedIndex > 0){
		var decimationInterval = parseInt(document.getElementById(this.id + '_downSamplingInterval').value);
		if(isNaN(decimationInterval) || decimationInterval<=0){
			alert("Wrong value for decimation interval.");
			return false;
		}
	}
	return true;
}

/**
	This is the main "submit" button. Some error checking is sone on channels before
	the asynchronous xml request is made.
 */
Menu.prototype.submit = function() {
	
	var t = getTimes();
	if (t == null)
		return;
	
	var form		= this.getForm();
	var chselect	= form.elements["selector:ch"];
	var rkselect	= form.elements["selector:rk"];
	var plotSeparately	= form.elements["plotSeparately"];
	
	// do validation on the channels input if it exists
	if (chselect) {
		
		// at least one channel must be selected
		if (chselect.selectedIndex == -1) {
			alert("You must select a channel.");
			return;
		}		
		if (chselect.selectedIndex >= 0 && chselect[chselect.selectedIndex].text.indexOf('[') != -1) {
			alert("Illegal channel.");
			return;
		}
	}

	// temporarily disable best possible rank requests (plotters haven't implemented this yet)
	if (rkselect) {
		// if (rkselect[rkselect.selectedIndex].value == 0) {
			// alert("Best Possible Rank not available at this time.");
			// return;
		// }
	}
	
	// create the plot request and plot component
	var pr = new PlotRequest();
	var pc = pr.createComponent(this.id, t.st, t.et);
	var compCount = pc.setFromForm(form);
	
	// update the sizes on the plots based on how many channels were chosen
	
	if(plotSeparately !=null && plotSeparately.value == "true"){
		pr.params.h	= (compCount*pc.chCnt * 150) + 60;
	} else {
		pr.params.h	= pc.chCnt * 150 + 60;
	}
	pc.h = 150;
	
	if (this.presubmit(pr, pc)) {
		loadXML(this.id + " plot", pr.getURL());
	}
}

/**
	Set and apply the geographicial filter for the selection in the first sub-menu.
	and for any map to be drawn
 */
Menu.prototype.filterChanged = function() {
	
	var form	= this.getForm();
	var select	= form.elements["selector:ch"];
	
	// if the channels for this menu have been previously loaded and stored in the allChannels variable
	if (this.allChannels) {
		
		// the array of all the geographic filters
		var wesn	= getGeoFilter();
		
		// the value of the channel type
		var ctid	= getChannelTypeFilter(form);
		
		// remove all the nodes from the select dropdown
		while (select.hasChildNodes()) {
			select.removeChild(select.firstChild);
		}
		
		// iterate through all channels and if it fits the geographic (and channel type) criteria then add it
		for (var i = 0; i < this.allChannels.length; i++) {

			var pt		= this.allChannels[i].value.split(":");
			var lon		= pt[3];
			var lat		= pt[4];
			var ctid1	= pt[6];
			var gfCheck	= false;
			var ctCheck	= false;
			
			// geographic filter check
			if (wesn == null || ((lon != "NaN" && lat != "NaN") && (wesn[0] < lon && wesn[1] > lon && wesn[2] < lat && wesn[3] > lat))) {
				gfCheck	= true;
			}
			
			// channel type check
			if (ctid == null || ctid == ctid1) {
				ctCheck	= true;
			}
			
			if (gfCheck && ctCheck) {
				select.appendChild(this.allChannels[i]);
			}
		}
		
		// if nothing matches the filter requirements then populate the dropdown appropriately
		if (select.options.length == 0) {
			var opt = document.createElement('option');
			opt.appendChild(document.createTextNode("[filter: no result]"));
			select.appendChild(opt);
		}
	}
}

/** 
	Run filterChanged based on the new menu chosen.
 */ 
Menu.prototype.menuFocused = function() {
	this.filterChanged();
}