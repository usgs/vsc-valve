/** @fileoverview deals with AJAX functionality, the XMLHttpRequest method and necessary handlers 
 * @author Dan Cervelli
 */


/**
 *  Use the "XMLHttpRequest" to make an asynchronous AJAX call. First detect if we're using
 *  Microsoft's ActiveX implementation (Internet Explorer 6 and earlier) to set up the right
 *  request object.
 *  
 *  Open the url passed to the function with an http GET request. Wait for the readystatechange
 *  with a cycling circle of circles wait-graphic animation. 
 *  If things look good send it off to the func or handleXML (see below.)
 *
 * @param {string} title example: "gpsmenu javascript"
 * @param {string} url example: "menu/gpsmenu.html"
 * @param {function} func function to run on load
 */
var numLoading = 0;
function loadXML(title, url, func)
{
	var req = null;
	if (window.XMLHttpRequest) {
		req = new XMLHttpRequest();
	} else if (window.ActiveXObject) {
		req = new ActiveXObject("Microsoft.XMLHTTP");
	}
		
	addURLLoading();

	req.onreadystatechange = function()
	{
		if (req.readyState == 4)
		{
			removeURLLoading();
			var status = -1;
			try { status = req.status; } catch (e) {}
			if (status != -1 && status == 200) {
				if (func)
					func(req);
				else 
					handleXML(req);
			} else {
				if (title) {
					alert("There was a problem loading component '" + title + "'.");
				} else {
					alert("There was a problem loading a component.");
				}
			}
		}
	}

	url = url.replace(/#/, "%23");
	req.open("GET", url, true);
	req.send(null);
}
/**
 * sends the results of an XML request to the appropriate handler, 
 * the menu handler or the plot handler
 *
 * @param {object} req request object for AJAX 
 */
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
/** 
 * Start the wait animation loading, a cycling circle of circles wait-graphic gif anim.
 */
function addURLLoading()
{
	numLoading++;
	var throbber = document.getElementById('throbber');
	if (throbber && throbber.src.indexOf('.png' != -1))
	{
		throbber.src = "images/throbber_anim.gif";			
	}
}

/**
 *  If all XML has returned (as tracked by the global variable numLoading), 
 *  whether successfully or not, replace the animated "throbber"
 *  with a static image.
 *
 *  @param {string} url doesn't seem to be used
 */
function removeURLLoading(url)
{
	numLoading--;
	if (numLoading <= 0)
	{
		var throbber = document.getElementById('throbber');
		if (throbber)
			throbber.src = "images/throbber_still.png";			
	}
}

/**
 *  Returns the value of an xml cell by tag. Get first index if index 
 *  parameter isn't included. 
 *  @param {text} xml xml to search
 *  @param {string} tag tag to search for
 *  @param {int} index to search for, if any
 *  @returns element data found
 *  @type object
 */
function getXMLField(xml, tag, index)
{
	if (!index)
		index = 0;
	var elt = xml.getElementsByTagName(tag)[index];
	if (elt)
		return elt.firstChild.data;
	else
		return null;
}

/**
 *  Write xml tree out in html readable format. You get this when you click the 'x' icon
 *  on a plot, for example.
 *
 *  @param {object} doc document object
 *  @param {object} tree document object to treat as a tree
 */
function xmlToHTML(doc, tree)
{
	if (tree)
	{
		if (tree.tagName)
			doc.write("&lt;" + tree.tagName + "&gt;");
		
		if (tree.childNodes.length > 1)
			doc.write("<br>");
		if (tree.data)
			doc.write(tree.data);
		for (var i = 0; i < tree.childNodes.length; i++)
			xmlToHTML(doc, tree.childNodes[i]);
	
		if (tree.tagName)
			doc.write("&lt;/" + tree.tagName + "&gt;<br>");
	} 
}

/**
 *  Search through all branches of xml tree for "url" tag, and return url
 *  found.
 *  @param {object} doc document object
 *  @param {object} tree document object
 *  @returns url found in xml
 *  @type string
 */
function xmlToURL(doc, tree)
{
	if (tree)
	{
		if (tree.tagName == "url")
			return tree.childNodes[0].data;
		
		for (var i = 0; i < tree.childNodes.length; i++)
		{
			var url = xmlToURL(doc, tree.childNodes[i]);
			if (url.length != "undefined")
				return url;
		}
	} 
}
