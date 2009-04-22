// $Id: box.js,v 1.1 2005/09/03 19:18:35 dcervelli Exp $

/** @fileoverview  
 * 
 * initialization of and control of interface elements with the box that contains 
 * the data source specific sub-menus 
 *
 * @author Dan Cervelli
 */

var IMG_COLLAPSE = "images/minus.png";
var IMG_EXPAND = "images/plus.png";

/**
 *  mapFunctionToTree calls itself to recursively traverse the tree, and 
 *  apply the passed 'func' to the element and all children of the element.
 *  root can be an element such as an html div named eTiltBox for example.
 *
 *  @param {element} root The root element to traverse and map function to
 *  @param {function} func Function to map to
 */
function mapFunctionToTree(root, func)
{
	if (root)
	{
		func(root);
		for (var i = 0; i < root.childNodes.length; i++)
			mapFunctionToTree(root.childNodes[i], func);
	} 
}

/**
 *  Incorporate passed ID parameter with all ids or htmlFors in an element.
 *  
 *  element can be an html div named eTiltBox for example, while uid = "msh_tilt"
 *  the element 'id' is changed from eTiltBox to msh_tilt_eTiltBox.
 *  
 *  All children of the element tree are checked and similarly renamed.
 *  
 *  For browsers or elements that use htmlFor the same sort of renaming happens 
 *  for that tag as well.
 *  
 *  @param {element} element The root element to traverse and label
 *  @param {string} uid The ID to tag this element with.
 *
 */
function unifyIds(element, uid)
{
	mapFunctionToTree(element,
		function(elt)
		{
			if (elt.id)
				elt.id = uid + "_" + elt.id;
			
			if (elt.tagName && elt.tagName.toLowerCase() == "label" && elt.htmlFor)
				elt.htmlFor = uid + "_" + elt.htmlFor;
		});
}

/**
 *  By changing the target element's style, show or hide the collapsible element
 *  after a mouse click on the + or -
 *
 *  @param {event} event The event that initiates the toggle
 */
function toggleCollapser(event)
{
	var target = getTarget(event);
	if (target.src.indexOf(IMG_COLLAPSE) != -1)
	{
		target.src = IMG_EXPAND;
		target.collapseTarget.style.display = "none";
	}
	else
	{
		target.src = IMG_COLLAPSE;
		target.collapseTarget.style.display = "block";
	}
}

/**
 *  This checks for a collapse element in the element or any of the element's children,
 *  if it finds one it adds the +/- image, and sets up the listener to toggle
 *  the open/collapse.
 *  
 *  For example, you have a certain data source. It has it's own set of menus (see
 *  the menu html/js pairs). If a collapser is included, this deals with initializing 
 *  it.
 *
 *  @param {element} element The root element to traverse and activate
 *  @param {string} uid The ID to retrieve this element with.
 */
function activateCollapsers(element, uid)
{
	// Create the match regular expression string to use later
	var cre = /collapse_(.*)$/;
	mapFunctionToTree(element,
		function(elt)
		{
			if (elt.id)
			{
				var re = elt.id.match(cre);
				if (re)
				{
					var img = document.createElement("img");
					img.src = IMG_COLLAPSE;
					var fc = elt.firstChild;
					elt.insertBefore(img, fc);
					elt.insertBefore(document.createTextNode(" "), fc);
					var expanded = false;
					if (re[1].charAt(re[1].length - 1) == "-")
					{
						re[1] = re[1].substring(0, re[1].length - 1);
						expanded = true;
					}
					
					var ct = document.getElementById(uid + "_collapseTarget_" + re[1]);
					img.collapseTarget = ct;
					img.src = IMG_EXPAND;
					ct.style.display = "none";
					if (expanded)
					{
						img.src = IMG_COLLAPSE;
						ct.style.display = "block";
					}
					
					addListener(img, 'click', toggleCollapser, false);
				}
			}
		});
}
/**
 *  By changing the event target element's style, show or hide the toggleable pane
 *
 *  @param {event} event The event that initiates the toggle
 */

function togglePane(event)
{
	var target = getTarget(event);
	if (!target.panes && target.htmlFor)
		target = document.getElementById(target.htmlFor);
	if (!target.panes)
		return;
	for (var i = 0; i < target.panes.length; i++)
	{
		var pane = target.panes[i];
		if (pane == target.paneToActivate)
			pane.style.display = "block";
		else
			pane.style.display = "none";
	}
}

/**
 *  This checks for a pane element or pane selector element
 *  in the passed element or any of the element's children;
 *  if it finds one it sets up the listener to toggle the open/hide.
 *  
 *  For example, you have a certain data source. It has it's own set of menus (see
 *  the menu html/js pairs). If a pane is included, this deals with initializing 
 *  it.
 *  
 *  @param {element} element The root element to traverse and activate
 *  @param {string} uid The ID to identify this element by.
 */
function activatePanes(element, uid)
{
	var pre = /pane_(.*)_(.*)$/;
	var panes = new Array();
	mapFunctionToTree(element,
		function(elt)
		{
			if (elt.id)
			{
				var re = elt.id.match(pre);
				if (re)
				{
					var pane = panes[re[1]];
					if (!pane)
						pane = panes[re[1]] = new Array();
					
					elt.style.display = "none";	
					if (re[2].charAt(re[2].length - 1) == '-')
					{
						elt.style.display = "block";
						re[2] = re[2].substring(0, re[2].length - 1);
					}
					
					pane[re[2]] = elt;
				}
			}
		});
		
	var psre = /^(.*):paneSelector_(.*)_(.*)$/;
	mapFunctionToTree(element,
		function(elt)
		{
			if (elt.id)
			{
				var re = elt.id.match(psre);
				if (re)
				{
					elt.id = re[1];
					if (elt.id == uid + "_")
						elt.id = null;
					
					elt.panes = panes[re[2]];
					elt.paneToActivate = panes[re[2]][re[3]];
					addListener(elt, 'click', togglePane, false);
				}
			}
		});
}
/**
 *  Do some general housekeeping and tweaking on a new box. Recurse through all box 
 *  elements and label the ids in a uniform manner. 
 *
 *  Activate the +/- collapsers, activate the menu panes having to do with this box.
 *  An example of a box can be the set of sub-menus needed between Data Sources and 
 *  Start Time, when you select a data source.
 *  
 *  @param {string} bid The box ID
 *  @param {string} uid The ID to identify this element by.
 */
function activateBox(bid, uid)
{
	var box = document.getElementById(bid);
	unifyIds(box, uid);
	
	activateCollapsers(box, uid);
	activatePanes(box, uid);
}