/** @fileoverview  
 * 
 * function menu for ewrsammenu.html 
 *
 * @author Dan Cervelli
 */

/**
 *  Called for ewrsammenu.html, allows access to form elements via menu object for RSAM data sources.
 *	Sets up time shortcut values for popup. 
 *
 *  @param {menu object} menu 
 */
create_ewrsammenu = function(menu) {
	
	menu.allowChannelMap	= true;
	menu.formName			= "ewrsamForm";
	menu.boxName			= "ewrsamBox";
	
	// make this work
	menu.initialize = function() {
		
		// default initialization
		Menu.prototype.initialize.call(this);
		
		// initialize the time shortcuts
		if (menu.timeShortcuts[0] == "") {
			menu.timeShortcuts	= new Array("-1m", "-6m", "-1y", "-2y", "-4y");
		}	
		
		loadXML(this.id + " generic menu description", "valve3.jsp?a=data&da=ewRsamMenu&src=" + this.id, 
			function(req) {
				var xml = req.responseXML;				
				var types = xml.getElementsByTagName("dataTypes")[0].firstChild.data;
				if (types == "[VALUES]")
				{
					document.getElementById(menu.id + "_mv").checked = "true";
					document.getElementById(menu.id + "_pane_options_0-").style.display = "block";
					document.getElementById(menu.id + "_pane_options_1").style.display = "none";
				}
				else if (types == "[EVENTS]")
				{
					document.getElementById(menu.id + "_cnts").checked = "true";
					document.getElementById(menu.id + "_pane_options_0-").style.display = "none";
					document.getElementById(menu.id + "_pane_options_1").style.display = "block";
				}
				else
				{
					document.getElementById(menu.id + "_outputTypeBox").style.display = "block";
				}
					
		});
	}
	
	menu.presubmit = function(pr, pc) {	
		
		// call the presubmit function
		Menu.prototype.presubmit.call(this);
		
		return true;
	}
}
