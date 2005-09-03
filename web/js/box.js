// $Id: box.js,v 1.1 2005-09-03 19:18:35 dcervelli Exp $

var IMG_COLLAPSE = "images/minus.png";
var IMG_EXPAND = "images/plus.png";

function mapFunctionToTree(root, func)
{
	if (root)
	{
		func(root);
		for (var i = 0; i < root.childNodes.length; i++)
			mapFunctionToTree(root.childNodes[i], func);
	} 
}

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

function activateCollapsers(element, uid)
{
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

function activateBox(bid, uid)
{
	var box = document.getElementById(bid);
	unifyIds(box, uid);
	
	activateCollapsers(box, uid);
	activatePanes(box, uid);
}