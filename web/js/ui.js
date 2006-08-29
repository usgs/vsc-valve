// $Id: ui.js,v 1.2 2006-08-29 00:01:43 tparker Exp $

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

function doSubmit()
{
	if (currentMenu == null)
		return;
	else
		currentMenu.submit();
}

function deleteAll()
{
	var cip = document.getElementById('contentInsertionPoint');
	while (cip && cip.childNodes.length > 0)
		cip.removeChild(cip.firstChild);
}

function setAdmin(admin, adminEmail)
{
	var a = document.getElementById('admin');
	a.href = "mailto:" + adminEmail;
	a.appendChild(document.createTextNode(admin));
}

function getChecked(array)
{
	for (var i = 0; i < array.length; i++)
	{
		if (array[i].checked)
			return i;
	}
	return -1;
}

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

function getTF(cb)
{
	if (cb.checked)
		return "T";
	else
		return "F";
}

var redLineVisible = false;

function enableRedLine()
{
	if (!redLineVisible)
	{
		redLineVisible = true;
		td1 = document.getElementById("redLine1").style.visibility = "visible";
		td2 = document.getElementById("redLine2").style.visibility = "visible";
	}
}

function disableRedLine()
{
	redLineVisible = false;
	td1 = document.getElementById("redLine1").style.visibility = "hidden";
	td2 = document.getElementById("redLine2").style.visibility = "hidden";
}

// This needs to be fixed to better set the lower height. clientHeight screwed up when scrolled.
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
