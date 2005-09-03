// $Id: xml.js,v 1.1 2005-09-03 19:18:35 dcervelli Exp $

var numLoading = 0;
function loadXML(title, url, func)
{
	var req = null;
	if (window.XMLHttpRequest)
		req = new XMLHttpRequest();
	else if (window.ActiveXObject)
		req = new ActiveXObject("Microsoft.XMLHTTP");
		
	addURLLoading();

	req.onreadystatechange = function()
	{
		if (req.readyState == 4)
		{
			removeURLLoading();
			var status = -1;
			try { status = req.status; } catch (e) {}
			
			if (status != -1 && status == 200)
			{
				if (func)
					func(req);
				else 
					handleXML(req);
			}
			else
			{
				if (title)
					alert("There was a problem loading component '" + title + "'.");
				else
					alert("There was a problem loading a component.");
			}
		}
	}
		
	req.open("GET", url, true);
	req.send(null);
}

function handleXML(req)
{
	var doc = req.responseXML;
	if (doc == null)
	{
		alert("No result.");
		return;
	}
	var result = doc.getElementsByTagName("valve3result")[0];
	if (result == null)
	{
		alert("No result.");
		return;
	}
	type = doc.getElementsByTagName("type")[0].firstChild.nodeValue;
	if (type == 'menu')
		handleMenu(doc);
	else if (type == 'plot')
		handlePlot(doc);
	else if (type == 'error')
	{
		alert(doc.getElementsByTagName("message")[0].firstChild.data);
	}
}

function addURLLoading()
{
	numLoading++;
	var throbber = document.getElementById('throbber');
	if (throbber.src.indexOf('.png' != -1))
	{
		throbber.src = "images/throbber_anim.gif";			
	}
}

function removeURLLoading(url)
{
	numLoading--;
	if (numLoading <= 0)
	{
		var throbber = document.getElementById('throbber');
		throbber.src = "images/throbber_still.png";			
	}
}

function getXMLField(xml, tag, index)
{
	if (!index)
		index = 0;
	return xml.getElementsByTagName(tag)[index].firstChild.data;
}