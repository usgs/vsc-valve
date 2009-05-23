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
				
				var colDiv = document.createElement('div');
				var detDiv = document.createElement('div');
				var norDiv = document.createElement('div');
				colDiv.className = 'm4';
				detDiv.className = 'fr m4';
				norDiv.className = 'fr m4';
				
				var p = document.createElement('p');
				var l = document.createTextNode('Name');
				p.appendChild(l);
				colDiv.appendChild(p);
				
				var p = document.createElement('p');
				var l = document.createTextNode('Detrend');
				p.appendChild(l);
				detDiv.appendChild(p);
				
				var p = document.createElement('p');
				var l = document.createTextNode('Normalize');
				p.appendChild(l);
				norDiv.appendChild(p);
				
				for (var i = 0; i < cols.length; i++) {
					
					// build the column checkbox
					var p		= document.createElement('p');					
					var col		= cols[i].firstChild.data.split(":");
					var label	= " " + col[2] + " (" + col[3] + ")";
					var el		= document.createElement('input');
					el.type		= 'checkbox';
					el.id		= menu.id + "_" + col[1];
					if (col[4] == "T") { el.checked = "checked"; }
					el.name		= col[1];
					p.appendChild(el);
					var tn		= document.createTextNode(label);
					p.appendChild(tn);
					colDiv.appendChild(p);
					
					// build the detrend checkbox
					var p		= document.createElement('p');
					p.className	= 'center';
					var el		= document.createElement('input');
					el.type		= 'checkbox';
					el.id		= menu.id + "_d_" + col[1];
					el.name		= "d_" + col[1];
					p.appendChild(el);
					detDiv.appendChild(p);
					
					// build the normalize checkbox
					var p		= document.createElement('p');
					p.className	= 'center';
					var el		= document.createElement('input');
					el.type		= 'checkbox';
					el.id		= menu.id + "_n_" + col[1];
					el.name		= "n_" + col[1];
					el.checked	= "checked";
					p.appendChild(el);
					norDiv.appendChild(p);
				}
				
				ip.appendChild(detDiv);
				ip.appendChild(norDiv);
				ip.appendChild(colDiv);
			});
	}
}
