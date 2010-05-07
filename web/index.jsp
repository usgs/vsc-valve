<%@ page language="java" %>
<%@ page import="gov.usgs.valve3.Valve3, java.util.Date" %>
<%@ page session="false" %>
<%@ page isThreadSafe="true" %>

<!DOCTYPE HTML PUBLIC "=-//W3C//DTD HTML 4.01//EN"
	"http://www.w3.org/TR/html4/strict.dtd">

<html>
	<head>
		<title>Valve Menu</title>
		<link rel="stylesheet" href="valve3.css">
		
		<script type="text/javascript" src="js/event.js"></script>		
		<script type="text/javascript" src="js/time.js"></script>		
		<script type="text/javascript" src="js/xml.js"></script>
		<script type="text/javascript" src="js/menu.js"></script>
		<script type="text/javascript" src="js/ui.js"></script>
		<script type="text/javascript" src="js/plot.js"></script>
		<script type="text/javascript" src="js/popup.js"></script>
		<script type="text/javascript" src="js/box.js"></script>
		<script type="text/javascript" src="js/xlat.js"></script>
		
		<script type="text/javascript">
		
		function onLoad()
		{
			var p = document.getElementById('timeShortcutPanel');
			addListener(p, 'click', timeShortcutClick, false);
			populateTimeShortcuts(new Array("-1i", "-1h", "-1d", "-1w", "-1m", "-1y"));
			addListener(document.getElementById('timeShortcutButton'), 'click', toggleTimeShortcutPanel, false);
			addListener(document.getElementById('minMaxUI'), 'click', toggleUI, false);
			addListener(document.getElementById('submit'), 'click', doSubmit, false);
			addListener(document.getElementById('deleteAll'), 'click', deleteAll, false);
			addListener(document.getElementById('nowLink'), 'click', function() { document.getElementById('endTime').value = 'Now'; }, false);

			var b = document.getElementById("channelMapButton");
			b.disabled = true;
			
			addListener(document.getElementById('geoFilter'), 'change', 
				function()
				{
					var wesn = getGeoFilter();
					b.disabled = wesn == null;
				});
			
			addListener(b, 'click',
				function()
				{
					var menu = "";
					if (currentMenu && currentMenu.allowChannelMap)
						menu = currentMenu.id;
					
					var pr = new PlotRequest();
					var pc = pr.createComponent(menu, 0, 1);
					pc.src = "channel_map";
					pc.subsrc = menu;
					var wesn = getGeoFilter();
					if (wesn == null)
					{
						alert("You must select a geographic filter to generate a channel map.");
						return;
					}
					pc.west = wesn[0];
					pc.east = wesn[1];
					pc.south = wesn[2];
					pc.north = wesn[3];
					loadXML("channel map plot", pr.getURL());
				});
			
			loadDataSources();
		}
	
		function ieWindowLoaded(event)  
		{
			document.body.ondrag = function () { return false; };
			document.body.onselectstart = function () { return false; };
		}
		
		addListener(window, 'load', onLoad, false);
		</script>
	</head>
	
	<% if (config.getServletContext().getResource("/header.html") != null) { %>
        <!-- <jsp:include page="header.html" /> -->
    <% } %>
	
	<body onload="ieWindowLoaded(event);">
		<div id="container">
		
		<div class="topBar">
			<img id="deleteAll" style="float: right" src="images/x.gif">
			<img id="minMaxUI" style="float: right" src="images/min.gif">
			<span id="appTitle"></span>
		</div>

		<div id="uiPanel">
			<!-- TODO: rework the data panel to use boxes -->
			<div id="dataPanel" class="dataPanel">
				<img class="fl" src="images/folders.gif"><h2>Data Sources</h2>
				<ul class="menu">
				</ul>
				<div class="box" style="margin: 0px 0px 4px 0px">
					<h1 class="filter">Geographic Filter</h1>				
					<select id="geoFilter" class="w100p">
						<option value="[none]">[none]</option>
					</select>
					<div class="center"><input type="button" value="Draw Map" id="channelMapButton"></div>
				</div>
			</div>
			
			<div id="openPanel" class="openPanel">
			</div>
			
			<div id="timePanel">
				<div class="box">
					<img src="images/clock_down.gif" class="fl" id="timeShortcutButton"><h1>Start Time</h1>
					<p><input class="mono" type="text" value="" id="startTime" size="17"></p>
				</div>
				<div class="box">
					<h1>End Time</h1>
					<p><input class="mono" type="text" value="Now" id="endTime" size="17"></p>
				</div>
				<!-- p>'yyyy[MMdd[hhmm]]' or '<a id="nowLink">Now</a>', in <a id="timeZoneAbbr">.</p-->
				<p>'yyyy[MMdd[hhmm]]' or '<a id="nowLink">Now</a>'.</p>
				<div class="box">
					<h1>Time Zone</h1>
					<p>
					<select id="timeZoneAbbr" name="selector:tz" class="w100p">
					<option>UTC</option>
					<option selected="selected"><%=Valve3.getInstance().getTimeZoneAbbr()%></option>
					</select>
					</p>
					<input type="hidden" value="<%=Valve3.getInstance().getTimeZoneOffset(new Date())%>" id="timeZoneOffset">
				</div>
				<div class="box">
					<h1>Output Size</h1>
					<p>
					<select id="outputSize" class="w100p">
					<option>Tiny</option>
					<option>Small</option>
					<option selected="selected">Medium</option>
					<option>Large</option>
					</select>
					</p>
				</div>
				<p><input id="submit" type="button" value="Submit"></p>
				<hr>
				<img id="throbber" style="float: right" src="images/throbber_still.png">
				<h5 id="version"></h5>
				<h5>Administrator: <a id='admin'></a></h5>
			</div>
			
			<div class="box listbox" id="timeShortcutPanel">
				<h1>Time Shortcuts</h1>
			</div>
		</div>
		
		<div style="clear:left;">		
			<div id="contentInsertionPoint"></div>
		</div>

		<div id='popupInsertionPoint'></div>
		<!-- Invisibile elements -->		
		<div id="contentTemplate" class="content">
			<img class="button" src="images/x.gif">
			<img class="button" src="images/min.gif">
			<img class="button" src="images/clock.gif">
			<img class="button" src="images/xml.gif">
			<img class="button" src="images/data.gif">
			<a target="plot" href=""><img class="button" src="images/bkmrk.gif"></a>
			<div class="titleBar">
				<h1>Content Template</h1>
			</div>
			<img class="pointer">	
		</div>
		
		<ul>
			<li id='listItemTemplate'>
				<h1><img src="images/plus.png"> Category</h1>
				<ul class="hiddenSubMenu">
				</ul>
			</li>
		</ul>
		
		<div id='popupTemplate' class="box abs">
			<img class="button" src="images/x.gif"><h1>Popup Title</h1>
			<div>
				<img>
			</div>
		</div>
		
		<div id='dataTemplate'>
			<iframe id="dataFrame"></iframe>
		</div>
		
		</div>
		
		<div id="redLine1"></div>
		<div id="redLine2"></div>
		
		<!--% if (config.getServletContext().getResource("/footer.html") != null) { %-->
			<!--  jsp:include page="footer.html" /-->
		<!--  % } %-->		
		
	</body>
</html>
