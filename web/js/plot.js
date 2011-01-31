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

/** 
 *  This function will fix the zoom marks if they are currently
 *  within the plot t, or if t is null.
 *	adj =  0: reset marks
 *	    = -1: fix for a minimized plot
 *	    =  1: fix for a maximized plot
 *  
 *  @param {t} If t is null or contains the marks, they will be fixed
 */
function fixZoomMarksInside(t,adj)
{
	var gmark = document.getElementById("greenMark");
	function reset( mark, newHome )
	{
		mark.style.visibility = "hidden";
		mark.parentNode.removeChild( mark );
		newHome.appendChild( mark );
	}
	function shrink( mark )
	{
		mark.style.visibility = "hidden";
	}
	function grow( mark )
	{
		mark.style.visibility = "visible";
	}
	if ( (t == null) || (gmark.parentNode == t) ) {
		var rmark = document.getElementById("redMark");
		if ( adj == 0 ) {
			var newHome = document.getElementById("container");
			reset( gmark, newHome );
			reset( rmark, newHome );
		} else if ( adj == 1 ) {
			grow( gmark );
			grow( rmark );
		} else {
			shrink( gmark );
			shrink( rmark );
		}
	}
}

var count = 0;
var dataCount = 0;
var combineMenuOpenedId = null;
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
	var combined = (getXMLField(xml, "combined") == "true");
	var waveform_type = (getXMLField(xml, "waveform") == "true");
	
	var t = document.getElementById('contentTemplate').cloneNode(true);
	t.id = "content" + count;
	t.style.width = (width * 1) + "px";
	var header = t.getElementsByTagName('h1')[0];
	t.getElementsByTagName('div')[4].id = t.id + 'combine';
	header.firstChild.nodeValue = title;
	var links = t.getElementsByTagName('a');
	var imgs = t.getElementsByTagName('img');
	var img = imgs[imgs.length - 1];
	var minImg = imgs[1];
	var components = xml.getElementsByTagName('component');
	t.getElementsByTagName('input')[0].value = components.length;
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
			if(combineMenuOpenedId===null){
				eval('translate_' + translationType + '(event)');
			} else {
				if(t.id == combineMenuOpenedId.replace('combine', '')){
					alert('You clicked the same graph');
				} else {
					var menu = document.getElementById(combineMenuOpenedId);
					menu.getElementsByTagName('font')[0].textContent = title + ' (' + t.id + ')'; 
					menu.getElementsByTagName('input')[1].disabled = false;
					menu.getElementsByTagName('input')[2].value = t.id;
					menu.getElementsByTagName('input')[3].value = title;
				}
			}
		}, false);
		
	// close
	addListener(imgs[0], 'click', 
		function()
		{
			fixZoomMarksInside(t,0);
			t.parentNode.removeChild(t);
			count--;
		}, false);
	
	// minimize
	addListener(imgs[1], 'click', 
		function()
		{
			if (minImg.src.indexOf("min.gif") != -1)
			{
				fixZoomMarksInside(t,-1);
				img.width = img.fullWidth / 4;
				img.height = img.fullHeight / 4;
				img.container.style.width = (img.fullWidth / 4) + "px";
				img.header.className = "min";
				minImg.src = 'images/max.gif';
			}
			else
			{
				fixZoomMarksInside(t,+1);
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
			var timeZoneOffset = 0;
			
			if(title.indexOf("Winston Helicorders")>=0){
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
				if(tzselect[tzselect.selectedIndex].text != "UTC"){
					timeZoneOffset = parseFloat(document.getElementById('timeZoneOffset').value);
				}
			}
			
			document.getElementById("startTime").value = buildTimeString(img.translation[4] + timeZoneOffset);
			document.getElementById("endTime").value = buildTimeString(img.translation[5] + timeZoneOffset);
		});
	
	// xml
	addListener(imgs[3], 'click',
		function()
		{
			var w = window.open('', 'xmlwin', 'menubar=0,toolbar=0,status=0,resizable=1,width=600,height=400,scrollbars=1');
			xmlToHTML(w.document, img.xml);
			w.document.close();
		});
		
	// processing data
	addListener(imgs[8], 'click',
		(function(t)
		{
			return function() {
				if ( t.style.visibility == "visible" )
					t.style.visibility = "hidden";
				else
					t.style.visibility = "visible";
			}
		})(t.getElementsByClassName("suppnodl")[0]));

    //Combination
    if(((components.length == 1) && (title.indexOf("Winston Helicorders")==-1) && (title.indexOf("Hypocenters")==-1)) || combined){
    	addListener(imgs[7], 'click',
    		function()
    		{
    			loadXML("combineMenu", "menu/combinemenu.html", function(req) {
    				var mip = document.getElementById(t.id+'combine');
    				if (req.readyState == 4 && req.status == 200) {
    					combineMenuOpenedId = t.id+'combine';
    					mip.innerHTML=req.responseText;
    					mip.style.display = "block";
      				}
    				var combineOKButton = mip.getElementsByTagName('input')[1];
        			addListener(combineOKButton, 'click',
        					function(){
         				    combineMenuOpenedId = null;
        					mip.style.display = 'none';
         					var plotSize = STANDARD_SIZES[mip.getElementsByTagName('select')[0].selectedIndex].slice(0);
         					var xScalingSelect = mip.getElementsByTagName('select')[1];
         					var yScalingSelect = mip.getElementsByTagName('select')[2];
         					var xScaling = parseFloat(xScalingSelect[xScalingSelect.selectedIndex].text);
         					var yScaling = parseFloat(yScalingSelect[yScalingSelect.selectedIndex].text);
         					plotSize[0] = plotSize[0]*xScaling; //plot width
         					plotSize[1] = plotSize[1]*yScaling; //plot height
         					plotSize[4] = plotSize[4]*xScaling; //comp width
         					plotSize[5] = plotSize[5]*yScaling; //comp height 
         					plotSize[6] = plotSize[6]*yScaling; //map height
         					var clickedId = mip.getElementsByTagName('input')[2].value;
         					var componentCount = document.getElementById(clickedId).getElementsByTagName('input')[0].value;
         					if(componentCount==1){
         						try{
         							loadXML(title + '+' + mip.getElementsByTagName('input')[3].value, combineUrl(url, document.getElementById(clickedId).getElementsByTagName('a')[0].href, plotSize));
         						} 
         						catch(err)
         						{
         							alert(err);
         						}
         					} else {
         						alert("Selected plot has more than 1 component");
         					}

         			});
        			var combineCancelButton = mip.getElementsByTagName('input')[0];
        			addListener(combineCancelButton, 'click',
        					function(){
        				    combineMenuOpenedId = null;
        					mip.style.display = 'none';
        			});
    			});
    			
      		});
    } else {
    	t.removeChild(imgs[7]);
    }
    
	// raw data
    if ( raw_ok && !combined ) {
    	// csv
		addListener(imgs[4], 'click',
			function()
			{
				var query = img.xml.getElementsByTagName("url")[0].childNodes[0].nodeValue;
				var url = "valve3.jsp?" + query.replace("a=plot", "a=rawData").replace("o=xml","o=csv");
				
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
		if ( waveform_type ) {
			// csvnots
			addListener(imgs[5], 'click',
				function()
				{
					var query = img.xml.getElementsByTagName("url")[0].childNodes[0].nodeValue;
					var url = "valve3.jsp?" + query.replace("a=plot", "a=rawData").replace("o=xml","o=csvnots");
					
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
			// seed
			addListener(imgs[6], 'click',
				function()
				{
					var query = img.xml.getElementsByTagName("url")[0].childNodes[0].nodeValue;
					var url = "valve3.jsp?" + query.replace("a=plot", "a=rawData").replace("o=xml","o=seed");
					
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
		} else {
			t.removeChild(imgs[6]);
			t.removeChild(imgs[5]);
		}
	} 
	else {
		t.removeChild(imgs[6]);
		t.removeChild(imgs[5]);
		t.removeChild(imgs[4]);
		//var ebs = t.getElementsByClassName('button');
		//var e;
		//for ( e in ebs ) {
		//	if ( ebs[e].getAttribute('name') == 'export_btn' ) {
		//		ebs[e].setAttribute( 'src', 'images/nocsv.gif' );
		//		ebs[e].setAttribute( 'class', 'button_off' );
		//		break;
		//	}
		//}
	}
	
    
	// setup direct link
	url = url.replace("o=xml", "o=png");
	var newURL = "valve3.jsp?" + url.substring(1);
	links[0].setAttribute('href', newURL);

	
	var ip = document.getElementById('contentInsertionPoint');
	ip.insertBefore(t, ip.firstChild);
		
	count++;

	// gather/format supplemental data
	var supp_data = xml.getElementsByTagName('suppdatum');
	var sbar = document.createElement( "div" );
	var node = 0;
	sbar.className = "suppdataline";
	var width = t.clientWidth*1;
	var height = t.clientHeight*1 + img.fullHeight*1 - 10;
	//sbar.style.height = (height-30) + "px";
	var sanchor = document.createElement( "div" );
	sanchor.className = "suppdataanchor";
	//sanchor.style.top = (height-20) + "px";
	sanchor.style.width = "10px";
	var proctable = t.getElementsByClassName( "suppnodltable" )[0];
	var procdata = proctable.getElementsByTagName('tbody')[0];
	for ( i=0; i<supp_data.length; i++ ) {
		var sd_text = supp_data[i].textContent;
		var sd_bits = sd_text.split( "\"" );
		if ( sd_bits[7] == "0" ) {
			var new_tr = document.createElement( "tr" );
			var new_td = document.createElement( "td" );
			new_td.appendChild( document.createTextNode( sd_bits[8] ) );
			new_tr.appendChild( new_td );
			var new_td = document.createElement( "td" );
			new_td.appendChild( document.createTextNode( timeToString(parseFloat(sd_bits[2])) + " thru " + timeToString(parseFloat(sd_bits[3])) ) );
			new_tr.appendChild( new_td );
			var new_td = document.createElement( "td" );
			var buttonnode= document.createElement('input');
			buttonnode.setAttribute('type','button');
			buttonnode.setAttribute('value','Show');
			new_td.appendChild(buttonnode);
			buttonnode.onclick = (function(sd) {return function(){ show_sd(sd) }})(t.id+"\""+sd_text);
			new_tr.appendChild( new_td );
			procdata.appendChild( new_tr );
			continue;
		}
		var color = "#" + sd_bits[14];
		sbar.style.background = color;
		//sanchor.style.background = color;
		var when = Math.round( translateT2X(parseFloat(sd_bits[3]), img) );
		var theNewParagraph = document.createElement('suppdatum');
		var theTextOfTheParagraph = document.createTextNode(supp_data[i].textContent);
		theNewParagraph.appendChild(theTextOfTheParagraph);
		theNewParagraph.style.visibility = "hidden";
		var bar_top = (sd_bits[15]*1 + 20) + "px";
		sbar.style.top = bar_top;
		var bar_height = (sd_bits[16]*1) + "px";
		sbar.style.height = bar_height;
		var anch_top = (sd_bits[15]*1 + sd_bits[16]*1) + "px";
		sanchor.style.top = anch_top;
		if ( when < width ) {
			sbar.style.left = when + "px";
			node = sbar.cloneNode(true);
			node.style.backgroundImage="url(images/supp_et.gif)";
			t.insertBefore(node, t.firstChild);
			sanchor.style.left = (when-10) + "px";
			node = sanchor.cloneNode(true);
			node.style.backgroundImage="url(images/sd_end.png)";
			node.appendChild(theNewParagraph);
			node.show_data = t.id+"\""+sd_text;
			node.onclick = (function(sd) {return function(){ show_sd(sd) }})(node.show_data); //function() {show_sd(node)};
			t.insertBefore(node,t.firstChild);
		}
		when = Math.round( translateT2X(parseFloat(sd_bits[2]), img) );
		if ( when > 0 ) {
			sbar.style.left = when + "px";
			t.insertBefore(sbar, t.firstChild);
			sbar = sbar.cloneNode(true);
			sanchor.style.left = when + "px";
			node = sanchor.cloneNode(true);
			node.style.backgroundImage="url(images/sd_start.png)";
			node.appendChild(theNewParagraph);
			node.show_data = t.id+"\""+sd_text;
			node.onclick = (function(sd) {return function(){ show_sd(sd) }})(node.show_data); //function() {show_sd(node)};
			t.insertBefore(node,t.firstChild);
		}
	}
	var ig = new scrollableTable(proctable, 150, 200);
//	if ( node != 0 ) {
//		var ebs = t.getElementsByClassName('suppdataviewer');
//		ebs[0].style.display = 'visible';
//	}
}

function show_sd(me) {
	var info = me;
	var info_bits = info.split("\"");
	var content = document.getElementById( info_bits[0] );
	var viewer = content.getElementsByClassName( "suppviewer" )[0];
	viewer.style.visibility = "visible";
	var elts = viewer.getElementsByTagName("td");
	elts[1].onclick = (function(sv) {return function(){ sv.style.visibility = "hidden"; }})(viewer);
	elts[2].innerHTML = "<b>From</b> " + timeToString(parseFloat(info_bits[3])) + "<br><b>to</b> " + timeToString(parseFloat(info_bits[4]))
	elts[3].innerHTML = "<b>Type</b>: " + info_bits[14];
	elts[4].innerHTML = "<b>Data</b>: " + info_bits[9] + "<br><textarea rows=\"3\" cols=\"40\">" + info_bits[10] + "</textarea></td>";
}

function combineUrl(url1, url2, size){
	var tz1 = url1.match(/&tz=\w+/)[0].substr(4);
	var tz2 = url2.match(/&tz=\w+/)[0].substr(4);
	if(tz1 != tz2){
		throw "Timezones are differ";	
	}
	var url1_n = getIntParameter('n', url1);
	var url2_n = getIntParameter('n', url2);
	url1 = url1.replace("o=png", "o=xml&combine=true");
	url1 = url1.replace(/&w=\d+/, "&w=" + size[0]);
	url1 = url1.replace(/&h=\d+/, "&h=" + size[1]);
	url1 = updateParameter('n', url1_n + url2_n, url1);
	compParams = url1.match(/&\w+\.\d+=[-|\*|\w|\d]+/g);
	for ( i=0; i<compParams.length; i++ ) {
		var param = compParams[i].split('=')[0].substr(1);
		var paramNameSplitted = param.split('.');
		if(paramNameSplitted[0] == 'x'){
			url1 = updateParameter('x.'+ paramNameSplitted[1], size[2], url1);
		} else if(paramNameSplitted[0] == 'y'){
			url1 = updateParameter('y.'+ paramNameSplitted[1], size[3], url1);
		} else if(paramNameSplitted[0] == 'h'){
			url1 = updateParameter('h.'+ paramNameSplitted[1], size[5], url1);
		} else if(paramNameSplitted[0] == 'w'){
			url1 = updateParameter('w.'+ paramNameSplitted[1], size[4], url1);
		} else if(paramNameSplitted[0] == 'mh'){
			url1 = updateParameter('mh.'+ paramNameSplitted[1], size[6], url1);
		}	
	}
	
	url2 = url2.substr(url2.search(/&\w+\.0=/));
	compParams = url2.match(/&\w+\.\d+=[-|\*|\w|\d]+/g);
	for ( i=0; i<compParams.length; i++ ) {
		var param = compParams[i].split('=');
		var paramNameSplitted = param[0].substr(1).split('.');
		var paramIndex = parseInt(paramNameSplitted[1]);
		url2 = removeParameter(param[0].substr(1), url2);
		if(paramNameSplitted[0] == 'x'){
			url2 = updateParameter('x.'+ (paramIndex+url1_n), size[2], url2);
		} else if(paramNameSplitted[0] == 'y'){
			url2 = updateParameter('y.'+ (paramIndex+url1_n), size[3], url2);
		} else if(paramNameSplitted[0] == 'h'){
			url2 = updateParameter('h.'+ (paramIndex+url1_n), size[5], url2);
		} else if(paramNameSplitted[0] == 'w'){
			url2 = updateParameter('w.'+ (paramIndex+url1_n), size[4], url2);
		} else if(paramNameSplitted[0] == 'mh'){
			url2 = updateParameter('mh.'+ (paramIndex+url1_n), size[6], url2);
		} else {
			url2 = updateParameter(paramNameSplitted[0]+'.'+ (paramIndex+url1_n), param[1], url2);
		}
	}
	return 'valve3.jsp?' + url1 + url2;
}

function getIntParameter(param, url){
	var regexp = new RegExp('&'+ param + '=[-|\\*|\\w|\\d]+');
	var paramStrings = url.match(regexp);
	if(paramStrings === null){
		throw 'Parameter "' + param + '" not found';
	}
	return parseInt(paramStrings[0].substr(param.length+2));
}

function updateParameter(param, newValue, url){
	var regexp = new RegExp('&'+ param + '=[-|\\*|\\w|\\d]+');
	var paramStrings = url.match(regexp);
	if(paramStrings === null){
		url = url + '&'+param+'='+newValue;
	}
	return url.replace(regexp, '&'+param+'='+newValue);
}

function removeParameter(param, url){
	var regexp = new RegExp('&'+ param + '=[-|\\*|\\w|\\d]+');
	var paramStrings = url.match(regexp);
	if(paramStrings === null){
		throw 'Parameter "' + param + '" not found';
	}
	return url.replace(regexp, '');
}

/**
 *	Initialize some defaults for the Valve page
 *	plot width, plot height, comp x, comp y, comp width, comp height, map height
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
			var compUnselected = 0;
			var compSelected = 0;
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
							
							// if ( elt.selectedIndex != -1 )
								// comp["selectedStation"] = elt.options[elt.selectedIndex].text;
								
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
							if((elt.offsetHeight != 0) && (elt.id.indexOf('_dmo_')==-1)){
								compSelected = compSelected+1;
							}
						} else {
							comp[elt.name] = "F";
							if((elt.offsetHeight != 0) && (elt.id.indexOf('_dmo_')==-1)){
								compUnselected = compUnselected+1;
							}
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
			//where isn't components in form at all
			if((compSelected==0) && (compUnselected==0))
				compSelected = 1;
			
			return compSelected;
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
