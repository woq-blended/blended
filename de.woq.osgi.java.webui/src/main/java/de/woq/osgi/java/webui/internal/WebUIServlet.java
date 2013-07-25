package de.woq.osgi.java.webui.internal;

import de.woq.osgi.java.container.context.ContainerContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class WebUIServlet extends HttpServlet {

  private ContainerContext containerContext;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
  }


}
