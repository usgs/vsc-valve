
create_nwismenu = function(menu) {
	menu.formName			= "genericVariableForm";
	menu.boxName			= "genericVariableBox";
	
	menu.initialize = function() {
		
		// default initialization
		Menu.prototype.initialize.call(this);
		
		// initialize the time shortcuts
		if (menu.timeShortcuts[0] == "") {
			menu.timeShortcuts	= new Array("-10i","-30i","-1h","-6h","-12h","-1d","-3d","-1w","-1m","-1y");
		}
		
		var selnwis = document.getElementById(this.id + '_nwisSelector');
		selnwis.myId = this.id;
		
		var sel = document.getElementById(this.id + '_selector:ds');		
		addListener(sel, 'change', 
			function() {
				var interval = document.getElementById(this.id + '_interval');
				switch(sel.selectedIndex)
				{
					case 0:
						interval.firstChild.textContent = "Interval:";
						break;
					case 1:
						interval.firstChild.textContent = "Interval, pts: ";
						break;
					case 2:
						interval.firstChild.textContent = "Interval, sec: ";
						break;
				}
			}, false);
	}
	
	menu.presubmit = function(pr, pc) {	
		
		var f = this.getForm();
		
		var noChecked = 0;
		var selectedTypes = "";
		var cb = document.getElementsByName("dataType");
		if (cb == null)
			var l = 0;
		else
			var l = cb.length;
					
		if (l == 1) {
			if (f.elements['dataType'].checked)
				noChecked++;
		} else {
			for (var i = 0; i<l; i++) {
				if (f.elements['dataType'][i].checked) {
					selectedTypes += f.elements['dataType'][i].value + ":";
					noChecked++;
				}
			}
		}
		if (noChecked < 1) {
			alert("You must select at least one component.");
			return false;
		} else if (noChecked > 2) {
			alert("You can select at most 2 components.");
			return false;
		}
		
		// call the main presubmit function
		if (!Menu.prototype.presubmit.call(this)) {
			return false;
		} else {		
			return true;
		}	
	}
	
}

update_nwis_dataTypes = function(event) {
	var ev = getEvent(event);
	var t = getTarget(ev);
	var id = t.myId;
	var val = t.options[t.selectedIndex].value.split(":");
		 		 
	types = val[5].split("$");
	var typesBox = document.getElementById(id + '_dataTypes');
		 		 
	while (typesBox.hasChildNodes())
		typesBox.removeChild(typesBox.lastChild);
 		 
	var checkBox = new Array();
	for (var i=0; i<types.length; i++) {
		var thisType = types[i].split("=");
		var checkBox = document.createElement("input");
		checkBox.type = "checkbox";
		checkBox.name = "dataType";
		checkBox.id = "dataType";
		checkBox.value = thisType[0];
		p = document.createElement("p");
		p.appendChild(checkBox);
		p.appendChild(document.createTextNode(" " + thisType[1]));
		typesBox.appendChild(p);
	}
}
