// $Id: gpsmenu.js,v 1.2 2005-09-06 20:19:31 dcervelli Exp $

create_gpsmenu = function(menu)
{
	menu.allowChannelMap = true;
	menu.formName = "gpsForm";
	menu.boxName = "gpsBox";
	menu.selector = "selector:bm";
	menu.timeShortcuts = new Array("-1m", "-3m", "-6m", "-1y", "-2y", "-3y");

	menu.presubmit = function(pr, pc)
	{
		var f = this.getForm();
		var baseline = f.bl;
		if (baseline.value != '[none]');
		{
			for (var i = 0; i < f.options.length; i++)
			{
				var s = f.options[i].value.split(":");
				if (s[3] == baseline.value)
					pc.bl = s[0];
			}
		}
		var comp = getTF(f.east) + getTF(f.north) + getTF(f.up) + getTF(f.len);
		if (comp == "FFFF")
		{
			alert("You must select at least one component.");
			return false;
		}
		var comps = 0;
		for (var i = 0; i < comp.length; i++)
			if (comp[i] == 'T')
				comps++;

		pr.params.h = comps * 150 + 60;
		pc.h = comps * 150;
		return true;
	}

	menu.initialize = function()
	{
		Menu.prototype.initialize.call(this);
		
		var f = this.getForm();
		var bm = f[this.selector];
		
		addListener(document.getElementById(this.id + '_baselineButton'), 'click', 
			function()
			{
				if (bm.selectedIndex != -1)
					f.bl.value = bm[bm.selectedIndex].text;
			}, false);
			
		addListener(document.getElementById(this.id + '_clearButton'), 'click', 
			function()
			{
				if (bm.selectedIndex != -1)
					f.bl.value = "[none]";
			}, false);
	}
}
