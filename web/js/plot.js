// $Id: plot.js,v 1.12 2007/09/11 18:47:10 tparker Exp $
/**
 * @fileoverview responsible for waveform plots
 *
 * @author Dan Cervelli
 */


/**
 *  This function creates a popup/ zoomed-in plot for a waveform plot.
 *  It sets all the attributs of the popup panel.
 *  It is called by Plot Request
 * 
 *  @param {text} xml text defining the plot
 *  @param {integer} px x coordinate
 *  @param {integer} py y coordinate
 */
function createPopupPlot(xml, px, py)
{		
	px = px * 1;
	py = py * 1;
	var title = getXMLField(xml, "title");
	if (title == null)
	{
		alert("There was an error loading this popup.");
		return;
	}
	
	var src = getXMLField(xml, "file");
	var width = getXMLField(xml, "width") * 1;
	var height  = getXMLField(xml, "height") * 1;
	
	var popup = createPopup(px, py);
	var header = popup.getElementsByTagName('h1')[0];
	header.firstChild.nodeValue = title;
	var img = popup.getElementsByTagName('img')[1];
	img.src = src;
	img.width = width;
	img.height = height;
	img.xml = xml;
	
	var threshold = px + width + 20;
	if (threshold > document.body.clientWidth)
		popup.style.left = (px + (document.body.clientWidth - threshold)) + "px";
	
	popup.style.top = (py + 8) + "px";
	popup.style.width = (width * 1 + 4) + 'px';
	popup.style.height = (height * 1 + 24) + 'px';
	popup.style.display = "block";
}

var count = 0;
var dataCount = 0;
/** 
 *  This function is called by Plot Request and is responsible for 
 *  bringing a plot pane onto the screen, based on the xml from the request.
 *  
 *  It sets up the div panel area, and reads the created png from the temp
 *  area where it resides on the tomcat server. It attaches listeners to the 
 *  menu bar icons of the created window. The translation is to translate the 
 *  image xy web coordinates to whatever coordinates are native to the image 
 *  being displayed.
 *  
 *  @param {text} xml text definining the plot
 */
function handlePlot(xml)
{
	var title = getXMLField(xml, "title");
	var src = getXMLField(xml, "file");
	var width = getXMLField(xml, "width");
	var height = getXMLField(xml, "height");
	var translationType = getXMLField(xml, "translation-type");
	var translation = getXMLField(xml, "translation");
	var url = getXMLField(xml, "url");
	var raw_ok = (getXMLField(xml, "exportable") == "true");
	
	var t = document.getElementById('contentTemplate').cloneNode(true);
	t.id = "content" + count;
	t.style.width = (width * 1) + "px";
	var header = t.getElementsByTagName('h1')[0];
	header.firstChild.nodeValue = title;
	var links = t.getElementsByTagName('a');
	var imgs = t.getElementsByTagName('img');
	var img = imgs[imgs.length - 1];
	var minImg = imgs[1];
	img.header = header;
	img.container = t;
	img.src = src;
	img.fullWidth = width;
	img.fullHeight = height;
	img.translation = translation.split(",");
	img.xml = xml;
	for (var i = 0; i < img.translation.length; i++)
		img.translation[i] = img.translation[i] * 1;
	
	addListener(img, 'mouseover', function() { enableRedLine(); }, false);
	addListener(img, 'mouseout', function() { disableRedLine(); window.status=""; }, false);
	addListener(img, 'mousemove', 
		function(event) 
		{ 
			moveRedLine(event); 
			var gxy = eval('getTranslation_' + translationType + '(event)');
			if (gxy)
			{
				window.status = gxy[2];
			}
		}, false);
	
	addListener(img, 'click', 
		function(event)
		{
			eval('translate_' + translationType + '(event)');
		}, false);
		
	// close
	addListener(imgs[0], 'click', 
		function()
		{
			t.parentNode.removeChild(t);
			count--;
		}, false);
	
	// minimize
	addListener(imgs[1], 'click', 
		function()
		{
			if (minImg.src.indexOf("min.gif") != -1)
			{
				img.width = img.fullWidth / 4;
				img.height = img.fullHeight / 4;
				img.container.style.width = (img.fullWidth / 4) + "px";
				img.header.className = "min";
				minImg.src = 'images/max.gif';
			}
			else
			{
				img.width = img.fullWidth;
				img.height = img.fullHeight;
				img.header.className = "";
				img.container.style.width = (img.fullWidth * 1) + "px";
				minImg.src = 'images/min.gif';
			}
		}, false);
	
	// clock
	addListener(imgs[2], 'click',
		function()
		{
			document.getElementById("startTime").value = buildTimeString(img.translation[4]);
			document.getElementById("endTime").value = buildTimeString(img.translation[5]);
		});
	
	// xml
	addListener(imgs[3], 'click',
		function()
		{
			var w = window.open('', 'xmlwin', 'menubar=0,toolbar=0,status=0,resizable=1,width=600,height=400,scrollbars=1');
			xmlToHTML(w.document, img.xml);
			w.document.close();
		});
		
	// raw data
    if ( raw_ok ) {
		addListener(imgs[4], 'click',
			function()
			{ 
				var query = img.xml.getElementsByTagName("url")[0].childNodes[0].nodeValue;
				var url = "valve3.jsp?" + query.replace("a=plot", "a=rawData");
			
				loadXML("rawData", url, function(req)
				{
					var doc = req.responseXML;
					if (doc != null)
					{
						var result = doc.getElementsByTagName("valve3result")[0];
						if (result != null)
						{
							type = doc.getElementsByTagName("type")[0].firstChild.nodeValue;
							if (type == 'error')
								alert(doc.getElementsByTagName("message")[0].firstChild.data);
							else if (type == 'rawData')
							{
								var d = document.getElementById('dataFrame');
								d.src = doc.getElementsByTagName("url")[0].firstChild.data;
							}
						}
					}
				});
			}, false);
	} 
	else {
		var ebs = t.getElementsByClassName('button');
		var e;
		for ( e in ebs ) {
			if ( ebs[e].getAttribute('name') == 'export_btn' ) {
				ebs[e].setAttribute( 'src', 'images/nodata.gif' );
				ebs[e].setAttribute( 'class', 'button_off' );
				break;
			}
		}
	}
		
	// setup direct link
	url = url.replace("o=xml", "o=png");
	var newURL = "valve3.jsp?" + url.substring(1);
	links[0].setAttribute('href', newURL);

	
	var ip = document.getElementById('contentInsertionPoint');
	ip.insertBefore(t, ip.firstChild);
		
	count++;
}

/**
 *	Initialize some defaults for the Valve page
 *	width, height, x, y, width, height, map height
 */
var POPUP_SIZE_INDEX = 4;
var STANDARD_SIZES = new Array(
	new Array(300, 100, 35, 19, 260, 70, 300),
	new Array(760, 200, 75, 19, 610, 140, 400),
	new Array(1000, 250, 75, 19, 850, 188, 600),
	new Array(1200, 350, 75, 19, 1050, 288, 800),
	new Array(600, 200, 60, 20, 500, 160, 1200));

/** 
 *  When the user requests a plot with the "Submit" button, or when the user clicks
 *  on an existing plot to zoom in on one area, this function is called.
 *  
 *  Set the plot size, elements, etc. create URL to request component information via
 *  jsp
 *
 *  @param {object} popup The popup object to write to
 */
function PlotRequest(popup)
{
	this.params		= new Object();
	this.components	= new Array(0);
	this.sizeIndex	= document.getElementById("outputSize").selectedIndex;
	if (popup)
		this.sizeIndex = POPUP_SIZE_INDEX;
	
	this.params.a = "plot";
	this.params.o = "xml";
	var tzselect  = document.getElementById('timeZoneAbbr');
	if(tzselect){
		// at least one time zone must be selected
		if (tzselect.selectedIndex == -1) {
			alert("You must select a time zone.");
			return;
		}
	} else {
		alert("Time zone not defined.");
		return;
	}
	this.params.tz = tzselect[tzselect.selectedIndex].text;
	
	this.setStandardSize = function()
	{
		var size = STANDARD_SIZES[this.sizeIndex];
		if (size)
		{
			this.params.w =	size[0];
			this.params.h =	size[1];
		}
	}
    /**
     *  @param {string} src image source
     *  @param {string} st Start Time
     *  @param {string} et End Time
     *
     *  @return array of strings of time shortcut labels
     *  @type associative array
     */
	this.createComponent = function(src, st, et) {
		var size	= STANDARD_SIZES[this.sizeIndex];
		var index	= this.components.length;
		var comp	= new Object();
		comp.x		= size[2];
		comp.y		= size[3];
		comp.w		= size[4];
		comp.h		= size[5];
		comp.mh		= size[6];
		comp.chCnt	= 1;
		
		if (src)
			comp.src	= src;
		if (st)
			comp.st		= st;
		if (et)
			comp.et		= et;
		this.components[index] = comp;

		comp.setFromForm = function(form) {
			var compCount = 0;
			// iterate through each element in the form
			for (var i = 0; i < form.elements.length; i++) {
				var elt = form.elements[i];
				
				// disregard inputs labeled skip
				if (elt.name && elt.name.indexOf("skip:") == -1) {
					
					// inputs of these type need to be parsed in the same way
					if (elt.type == "select-multiple" || elt.type == "select-one") {
						var text = true;
						var name = elt.name;
						
						// one type of specially named input
						if (elt.name.indexOf("value:") != -1) {
							text		= false;
							name		= elt.name.substring(6);
							comp[name]	= getSelected(elt, text);
							
						// another type of specially named input
						} else if (elt.name.indexOf("selector:") != -1) {
							name		= elt.name.substring(elt.name.indexOf(":") + 1);
							var val		= getSelected(elt);
							var vals	= val.split('^');
							
							// for channel inputs, count how many channels were selected
							if (elt.name.indexOf(":ch") != -1) {
								comp.chCnt = vals.length;
							}	
							
							comp["selectedStation"] = elt.options[elt.selectedIndex].text;
							if (!comp[name])
								comp[name] = "";
							
							for (var j = 0; j < vals.length; j++) {
								var ss = vals[j].split(':');
								if (comp[name])
									comp[name] += ',';
								comp[name] += ss[0];
								// grab data types for nwis
								if (ss[5])
									comp["dataTypes"] = ss[5].replace(/=/g, ":");									
							}
						} else {
							comp[name] = getSelected(elt, true);
						}
						
					} else if (elt.name.indexOf("dataType") != -1) {						
						if (elt.checked) {
							if (comp["selectedTypes"] == null)
								comp["selectedTypes"] = elt.value;
							else
								comp["selectedTypes"] += ":" + elt.value;
						}
						
					} else if (elt.type == "checkbox") {
						if(elt.checked){
							comp[elt.name] = "T";
							if(elt.offsetHeight != 0){
								compCount = compCount+1;
							}
						} else {
							comp[elt.name] = "F";
						}		
					} else if (elt.type == "text") {
						comp[elt.name] = elt.value;
						
					} else if (elt.type == "hidden") {
						comp[elt.name] = elt.value;
						
					} else if (elt.type == "radio") {
						if (elt.checked) 
							comp[elt.name] = elt.value;
					}
				}
			}
			return compCount;
		}
		return comp;
	}

    /**
     *  @return url
     *  @type string
     */
	this.getURL = function()
	{
		var url = "valve3.jsp?";
		this.params.n = this.components.length;
		for (var param in this.params)
			url += "&" + param + "=" + this.params[param];
			
		for (var i = 0; i < this.components.length; i++)
		{
			for (var param in this.components[i])
				if (typeof this.components[i][param] != 'function')
					url += "&" + param + "." + i + "=" + this.components[i][param];
		}
		return url;
	}
	
	this.setStandardSize();
	
	return this;
}
