<%--  
$Log: not supported by cvs2svn $
Revision 1.3  2005/10/14 21:35:50  dcervelli
Moved anti-cache stuff so everything should not be cached.

Revision 1.2  2005/09/07 00:06:16  dcervelli
Added title to HTML output.

Revision 1.1  2005/09/03 19:18:35  dcervelli
Initial commit.

--%>

<%@ page import="gov.usgs.valve3.*" %>
<%@ page import="gov.usgs.valve3.result.*" %>
<%@ page import="java.io.*" %>

<%
	response.setHeader("Cache-Control", "no-cache");
	response.setHeader("Pragma", "no-cache");
	response.setDateHeader("Expires", 0);
	
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
<head>
<title><%= plot.getTitle() %></title>
</head>
<body>
<img src="<%= plot.getFilename() %>">
</body>
</html>	
				<%
				break;
		}
	}
	else if (result instanceof Result)
	{
		Result res = (Result)result;
		response.setContentType("text/xml");
		out.println(res.toXML());
	}
%>

