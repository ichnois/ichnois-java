package io.ichnois.samples.servlet;

import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreLevel;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class SampleServlet extends HttpServlet {
  static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    // every event sent to ichnois gets decorated with HTTP request data like headers, request
    // parameters, url
    IchnoisCore.captureMessage("Some warning!", IchnoisCoreLevel.WARNING);

    resp.getWriter().println("Hello World");
  }
}
