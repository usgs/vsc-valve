<%--  
$Log: not supported by cvs2svn $
--%>

<%@ page import="gov.usgs.valve3.*" %>
<%@ page import="gov.usgs.valve3.result.*" %>
<%@ page import="java.io.*" %>

<%
	ActionHandler handler = Valve3.getInstance().getActionHandler();
	Object result = handler.handle(request);
	
	if (result == null)
	{
		%>
<html>
<body>
<p>Please use the <a href="valve3.html">main user interface</a>.</p>
</body>
</html>
		<%
	}
	else if (result instanceof Valve3Plot)
	{
		Valve3Plot plot = (Valve3Plot)result;
		switch(plot.getOutputType())
		{
			case XML:
				response.setHeader("Cache-Control", "no-cache");
				response.setHeader("Pragma", "no-cache");
				response.setDateHeader("Expires", 0);
				response.setContentType("text/xml");
				out.println(plot.toXML());
				break;
			case PNG:
				response.setContentType("image/png");
				OutputStream os = response.getOutputStream();
				InputStream is = new BufferedInputStream(new FileInputStream(plot.getLocalFilename()));
				byte[] buf = new byte[128 * 1024];
				int n;
				while ((n = is.read(buf)) != -1)
				{
					os.write(buf, 0, n);
				}
				os.flush();
				os.close();
				break;
			case HTML:
				%>
<html>
<body>
<img src="<%= plot.getFilename() %>">
</body>
</html>	
				<%
				break;
		}
	}
	else if (result instanceof Menu)
	{
		Menu menu = (Menu)result;
		response.setContentType("text/xml");
		out.println(menu.toXML());
	}
	else if (result instanceof List)
	{
		List list= (List)result;
		response.setContentType("text/xml");
		out.println(list.toXML());
	}
	else if (result instanceof ErrorMessage)
	{
		ErrorMessage error = (ErrorMessage)result;
		response.setContentType("text/xml");
		out.println(error.toXML());
	}
%>

