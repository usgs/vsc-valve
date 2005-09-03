// $Id: gpsmenu.js,v 1.1 2005-09-03 19:18:35 dcervelli Exp $

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
	}
}
