package de.woq.osgi.java.itestsupport;

import org.apache.commons.exec.LogOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContainerOutputStream extends LogOutputStream {

  private final Logger LOGGER = LoggerFactory.getLogger(ContainerOutputStream.class);

  @Override
  protected void processLine(String line, int level) {
    LOGGER.info(line);
  }
}
