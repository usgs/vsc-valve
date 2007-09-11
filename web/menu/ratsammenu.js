// $Id: ratsammenu.js,v 1.1 2007-09-11 18:46:00 tparker Exp $

create_ratsammenu = function(menu)
{
	menu.allowChannelMap = true;
	menu.formName = "ratsamForm";
	menu.boxName = "ratsamBox";
	menu.selector = "first_selector:ch";
	menu.secondSelector = "second_selector:ch";
	
	menu.timeShortcuts = new Array("-1h", "-2h", "-6h", "-12h", "-24h", "-2d", "-3d", "-1w", "-2w", "-1m");

//	menu.presubmit = function(pr, pc)
//	{
//		var f = this.getForm();
//		
//		var sel1 = f.elements['selector:ch'];
//		var sel2 = f.elements['selector2:ch'];
//		
//		var val1 = sel1.options[sel1.selectedIndex].value;		
//		val1 = val1.replace(/:.*$/, "");
//		val1 = val1.replace(/\s.*$/, "");
//		
//		var val2 = sel2.options[sel2.selectedIndex].value;
//		val2 = val2.replace(/:.*$/, "");
//		
//		sel1.options[sel1.selectedIndex].value = val1 + "+" + val2;
//		return true;		
//	}
	
	menu.loadChannels = function()
	{
		Menu.prototype.loadChannels.call(this);
		// hackily change selector box name
	}
}
