
create_nwismenu = function(menu)
{
	menu.allowChannelMap = true;
	menu.formName = "nwisForm";
	menu.boxName = "nwisBox";
	menu.selector = "selector:ch";
	menu.timeShortcuts = new Array("-6h", "-12h", "-24h", "-2d", "-3d", "-1w", "-2w", "-1m", "-2m", "-6m");
	
	menu.initialize = function()
	{
		Menu.prototype.initialize.call(this);
		var sel = document.getElementById(this.id + '_nwisSelector');
		sel.myId = this.id;
	}
	
	menu.presubmit = function(pr, pc)
	{
		var f = this.getForm();
		
		var noChecked = 0;
		var selectedTypes = "";
		
		for (var i = 0; i<f.dataType.length; i++)
		{
			if (f.dataType[i].checked)
			{
				selectedTypes += f.dataType[i].value + ":";
				noChecked++;
			}
		}
		
		if (noChecked < 1)
		{
			alert("You must select at least one component.");
			return false;
		}
		else if (noChecked > 2)
		{
			alert("You can select at most 2 components.");
			return false;
		}	
		return true;		
	}
	
	menu.loadChannels = function()
	{
		Menu.prototype.loadChannels.call(this);
		// hackily change selector box name
	}
	
}

update_nwis_dataTypes = function(event)
{
	var ev = getEvent(event);
	var t = getTarget(ev);
	var id = t.myId;
	var val = t.options[t.selectedIndex].value.split(":");
		 		 
	types = val[5].split("$");
	var typesBox = document.getElementById(id + '_dataTypes');
		 		 
	while (typesBox.hasChildNodes())
		typesBox.removeChild(typesBox.lastChild);
 		 
	var checkBox = new Array();
	for (var i=0; i<types.length; i++)
	{
		var thisType = types[i].split("=");
		var checkBox = document.createElement("input");
		checkBox.type = "checkbox";
		checkBox.name = "dataType";
		checkBox.value = thisType[0];
		p = document.createElement("p");
		p.appendChild(checkBox);
		p.appendChild(document.createTextNode(thisType[1]));
		typesBox.appendChild(p);
	}
}
