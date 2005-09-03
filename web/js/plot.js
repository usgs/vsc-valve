// $Id: plot.js,v 1.1 2005-09-03 19:18:35 dcervelli Exp $

function createPopupPlot(xml, px, py)
{		
	px = px * 1;
	py = py * 1;
	var title = getXMLField(xml, "title");
	var src = getXMLField(xml, "file");
	var width = getXMLField(xml, "width") * 1;
	var height = getXMLField(xml, "height") * 1;
	
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
function handlePlot(xml)
{
	var title = getXMLField(xml, "title");
	var src = getXMLField(xml, "file");
	var width = getXMLField(xml, "width");
	var height = getXMLField(xml, "height");
	var translationType = getXMLField(xml, "translation-type");
	var translation = getXMLField(xml, "translation");
	
	var t = document.getElementById('contentTemplate').cloneNode(true);
	t.id = "content" + count;
	t.style.width = (width * 1) + "px";
	var header = t.getElementsByTagName('h1')[0];
	header.firstChild.nodeValue = title;
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
	addListener(img, 'mousemove', function(event) { moveRedLine(event); }, false);
	addListener(img, 'mouseout', function() { disableRedLine(); }, false);
	
	addListener(img, 'click', 
		function(event)
		{
			eval('translate_' + translationType + '(event)');
		}, false);
		
	addListener(imgs[0], 'click', 
		function()
		{
			t.parentNode.removeChild(t);
			count--;
		}, false);
	
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
	
	addListener(imgs[2], 'click',
		function()
		{
			document.getElementById("startTime").value = buildTimeString(img.translation[4]);
			document.getElementById("endTime").value = buildTimeString(img.translation[5]);
		});
	
	/*	
	addListener(imgs[2], 'click',
		function()
		{
			var xmlSerializer = new XMLSerializer();
			var markup = xmlSerializer.serializeToString(img.xml);
			alert(markup);
		});
		*/
	
	var ip = document.getElementById('contentInsertionPoint')
	ip.insertBefore(t, ip.firstChild);
		
	count++;
}

/*
function getStandardSize(index)
{
	var size = document.getElementById("outputSize").selectedIndex;
	var ss = "";
	switch(size)
	{
		case 0: // tiny
			ss = "&w=300&h=100&x." + index + "=35&y." + index + "=9&w." + index + "=230&h." + index + "=70";
			break;
		case 1: // small
			ss = "&w=760&h=200&x." + index + "=75&y." + index + "=19&w." + index + "=610&h." + index + "=140";
			break;
		case 3: // large
			ss = "&w=1200&h=350&x." + index + "=75&y." + index + "=19&w." + index + "=1050&h." + index + "=288";
			break;
		case 2: // medium
		default:
			ss = "&w=1000&h=250&x." + index + "=75&y." + index + "=19&w." + index + "=850&h." + index + "=188";
	}
	return ss;
}
*/

var POPUP_SIZE_INDEX = 4;
var STANDARD_SIZES = new Array(
	new Array(300, 100, 35, 19, 260, 70, 300),
	new Array(760, 200, 75, 19, 610, 140, 400),
	new Array(1000, 250, 75, 19, 850, 188, 600),
	new Array(1200, 350, 75, 19, 1050, 288, 800),
	new Array(600, 200, 60, 20, 500, 160, 1200));
	
function PlotRequest(popup)
{
	this.params = new Object();
	this.components = new Array(0);
	this.sizeIndex = document.getElementById("outputSize").selectedIndex;
	if (popup)
		this.sizeIndex = POPUP_SIZE_INDEX;
	
	this.params.a = "plot";
	this.params.o = "xml";
	
	this.setStandardSize = function()
	{
		var size = STANDARD_SIZES[this.sizeIndex];
		if (size)
		{
			this.params.w =	size[0];
			this.params.h =	size[1];
		}
	}

	this.createComponent = function(src, st, et)
	{
		var size = STANDARD_SIZES[this.sizeIndex];
		var index = this.components.length;
		var comp = new Object();
		comp.x = size[2];
		comp.y = size[3];
		comp.w = size[4];
		comp.h = size[5];
		comp.mh = size[6];
		if (src)
			comp.src = src;
		if (st)
			comp.st = st;
		if (et)
			comp.et = et;
		this.components[index] = comp;

		comp.setFromForm = function(form)
		{
			for (var i = 0; i < form.elements.length; i++)
			{
				var elt = form.elements[i];
				if (elt.name && elt.name.indexOf("skip:") == -1)
				{
					if (elt.type == "select-multiple" || elt.type == "select-one")
					{
						var text = true;
						var name = elt.name;
						if (elt.name.indexOf("value:") != -1)
						{
							text = false;
							name = elt.name.substring(6);
							comp[name] = getSelected(elt, text);
						}
						else if (elt.name.indexOf("selector:") != -1)
						{
							name = elt.name.substring(9);
							var val = "" + getSelected(elt);
							var vals = val.split(',');
							comp[name] = "";
							for (var j = 0; j < vals.length; j++)
							{
								var ss = vals[j].split(':');
								if (comp[name])
									comp[name] += ',';
								comp[name] += ss[0];
							}
						}
						else
							comp[name] = getSelected(elt);
					}
					else if (elt.type == "checkbox")
						comp[elt.name] = elt.checked ? "T" : "F";
					else if (elt.type == "text")
						comp[elt.name] = elt.value;
					else if (elt.type == "radio")
					{
						if (elt.checked) 
							comp[elt.name] = elt.value;
					}
				}
			}
		}
		
		return comp;
	}

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
