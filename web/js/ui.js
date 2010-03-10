// $Id: ui.js,v 1.2 2006/08/29 00:01:43 tparker Exp $

/** @fileoverview various user interface functions 
 * @author Dan Cervelli
 */

/**
 *  From the main valve tool interace bar, this hides or shows the 
 *  menu elements, but leaves the plots or maps showing. (Click minimize
 *  or maximize icons.)
 */
function toggleUI()
{
	if (document.getElementById('uiPanel').style.display == 'none')
	{
		document.getElementById('minMaxUI').src = "images/min.gif";
		document.getElementById('uiPanel').style.display = 'block';
	}
	else
	{
		document.getElementById('minMaxUI').src = "images/max.gif";
		document.getElementById('uiPanel').style.display = 'none';
	}
}

/**
 * if the current menu exists, execute a submit. Otherwise do nothing.
 */
function doSubmit()
{
	if (currentMenu == null)
		return;
	else
		currentMenu.submit();
}

/** 
 *  Deletes all plots or maps that have been drawn to the screen. This is
 *  somewhat non-standardly accessed by clicking the X in the close *  box of
 *  the main valve interface menu. The menu itself doesn't go away.
 *  
 *  This does not delete popup plots that come up when you click on a plot
 */
function deleteAll()
{
	var cip = document.getElementById('contentInsertionPoint');
	while (cip && cip.childNodes.length > 0)
		cip.removeChild(cip.firstChild);
}

/**
 *  Shows the administrator name and link to email address on the page as 
 *  configured in valve3/WEB-INF/config/valve3.config
 *  
 *  @param {string} admin Administrator name
 *  @param {string} adminEmail Administrator email address
 */
function setAdmin(admin, adminEmail)
{
	var a = document.getElementById('admin');
	a.href = "mailto:" + adminEmail;
	a.appendChild(document.createTextNode(admin));
}

/**
 *  Returns the index number of the item in an array which is checked.
 *
 *  @param {array} array an array of checkboxes or radio buttons
 *  @returns index number of the checked item
 *  @type int
 */
function getChecked(array)
{
	for (var i = 0; i < array.length; i++)
	{
		if (array[i].checked)
			return i;
	}
	return -1;
}

/**
 *  return an array of the name and value of the selected items in a 
 *  menu listing for example
 *
 *  @param {array} array an array of document elements
 *  @param {string} text name where to store the parameters 
 *  @returns joined string of name/value pairs
 *  @type string
 */
function getSelected(array, text)
{
	var list = new Array(1);
	var count = 0;
	for (var i = 0; i < array.length; i++)
	{
		if (array[i].selected)
		{
			if (text)
				list[count++] = array[i].text;
			else
				list[count++] = array[i].value;
		}
	}
	return list.join("^");
	//return list;
}

/**
 *	Check a form element to see if it's checked or not, and return T or F.
 *  @param {object} cb document object
 *  @returns T or F, so perhaps this isn't really a true boolean
 *  @type boolean
 */
function getTF(cb)
{
	if (cb.checked)
		return "T";
	else
		return "F";
}

var redLineVisible = false;
/**
 *	When you mouse-over a plot, the vertical red line is made visible
 */
function enableRedLine()
{
	if (!redLineVisible)
	{
		redLineVisible = true;
		td1 = document.getElementById("redLine1").style.visibility = "visible";
		td2 = document.getElementById("redLine2").style.visibility = "visible";
	}
}

/**
 *	When you mouse-out of a plot, the vertical red line is hidden
 */
function disableRedLine()
{
	redLineVisible = false;
	td1 = document.getElementById("redLine1").style.visibility = "hidden";
	td2 = document.getElementById("redLine2").style.visibility = "hidden";
}

// This needs to be fixed to better set the lower height. clientHeight screwed up when scrolled.
/**
 * Moves the vertical red line over the screen as the mouse moves.
 *
 * @param {event object} e event such as mouseover
 */
function moveRedLine(e)
{
	if (redLineVisible)
	{
		var ev = getEvent(e);
		var td1 = document.getElementById("redLine1");
		var td2 = document.getElementById("redLine2");
		var scroll = getScroll();
	
		td1.style.top="0px";
		td1.style.left=scroll[0] + ev.clientX + "px";
		td1.style.height=(document.body.clientHeight-(document.body.clientHeight-ev.clientY+10)+scroll[1])+ "px"; 
	
		td2.style.left=scroll[0] + ev.clientX + "px";
		td2.style.top=scroll[1] + ev.clientY+7 + "px";
		h = document.body.clientHeight-ev.clientY-70;
		td2.style.height=(h < 0 ? 0 : h) + "px";
	}
}
