// $Id: genericmenu.js,v 1.1 2005/10/20 05:08:40 dcervelli Exp $
/** @fileoverview  
 * 
 * function menu for genericfixedmenu.html 
 *
 * @author Dan Cervelli
 */

/**
 *  Called for genericfixedmenu.html, allows access to form elements via menu object
 *  Sets up time shortcut values for popup.
 *  
 *  Also, this tries to create and display a menu defined by an XML file for this
 *  generic data source.
 *  
 *  @param {menu object} menu 
 */
create_genericfixedmenu = function(menu) {
	menu.allowChannelMap	= true;
	menu.formName			= "genericForm";
	menu.boxName			= "genericBox";
	menu.selector			= "selector:ch";
	menu.timeShortcuts		= new Array("-1h", "-2h", "-6h", "-12h", "-24h", "-2d", "-3d", "-1w", "-2w", "-1m");
	
	// TODO: labelize checkboxes
	menu.initialize = function() {
		Menu.prototype.initialize.call(this);
		loadXML(this.id + " generic menu description", "valve3.jsp?a=data&da=genericMenu&src=" + this.id, 
			function(req) {
				var xml = req.responseXML;				
				var ip = document.getElementById(menu.id + "_insertionPoint");
				var cols = xml.getElementsByTagName("column");
				for (var i = 0; i < cols.length; i++) {
					var p = document.createElement('p');					
					var col = cols[i].firstChild.data.split(":");
					var label = " " + col[2] + " (" + col[3] + ")";
					var cb = document.createElement('input');
					cb.type = 'checkbox';
					cb.id = menu.id + "_" + col[1];
					if (col[4] == "T") {
						cb.checked = "checked";
					}
					cb.name = col[1];
					p.appendChild(cb);
					var tn = document.createTextNode(label);
					p.appendChild(tn);
					
					ip.appendChild(p);
				}
			});
	}
}
