package de.woq.osgi.java.itestsupport;

import java.util.ArrayList;
import java.util.List;

import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.CompositeOption;
import org.ops4j.pax.exam.options.DefaultCompositeOption;
import org.ops4j.pax.exam.options.UrlProvisionOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompositeBundleListProvider implements BundleListProvider {

  private final String[] composites;
  private List<String> traversedComposites = new ArrayList<>();

  private final static Logger LOGGER = LoggerFactory.getLogger(CompositeBundleListProvider.class);

  private final static String TOKEN_COMPOSITE = "scan-composite:";
  private final static String TOKEN_BUNDLE    = "scan-bundle:";

  private final static String[] IGNORE_PATTERNS = new String [] {
    "mvn:org.slf4j(.)*"
  };

  public CompositeBundleListProvider(final String...composites) {
    this.composites = composites;
  }

  @Override
  public CompositeOption getBundles() throws Exception {

    List<Option> configuredBundles = new ArrayList<Option>();

    for(String composite: composites) {
      Option o = resolveComposite(composite);
      if (o != null) {
        configuredBundles.add(o);
      }
    }
    return new DefaultCompositeOption(configuredBundles.toArray(new Option[] {}));
  }

  private Option resolveComposite(final String composite) throws Exception {
    Option result = null;

    if (traversedComposites.contains(composite)) {
      LOGGER.info("Composite [" + composite + "] already traversed. Ignoring duplicate occurence.");
    } else {
      LOGGER.info("Traversing composite [" + composite + "]");
      traversedComposites.add(composite);

      List<Option> configuredBundles = new ArrayList<>();
      for(String entry: CompositeFileReader.compositeEntries(composite)) {
        if (entry.startsWith(TOKEN_COMPOSITE)) {
          configuredBundles.add(resolveComposite(entry.substring(TOKEN_COMPOSITE.length())));
        } else if (entry.startsWith(TOKEN_BUNDLE)) {
          configuredBundles.add(resolveEntry(entry.substring(TOKEN_BUNDLE.length())));
        } else {
          throw new Exception(String.format(
            "Unable to parse entry [%s] in composite file [%s]", composite, entry)
          );
        }
      }

      result = new DefaultCompositeOption(configuredBundles.toArray(new Option[] {}));
    }

    return result;
  }

  private Option resolveEntry(final String entry) {

    LOGGER.info("Resolving entry [" + entry + "]");

    String[] tokens = entry.split("@");
    String url = tokens[0];

    if (ignored(url)) {
      LOGGER.info("Ignoring url [" + url + "]");
      return null;
    }

    int sl = -1;
    boolean nostart = false;

    if (tokens.length > 1) {
      for(int i=1; i<tokens.length; i++) {
        try {
          sl = Integer.parseInt(tokens[i]);
        } catch (Exception nfe) {
          //Ignore this
        }

        if ("nostart".equals(tokens[i])) {
          nostart = true;
        }
      }
    }

    UrlProvisionOption result = new UrlProvisionOption(url);

    if (sl != -1) {
      result.startLevel(sl);
    }

    if (nostart) {
      result.noStart();
    }

    return result;
  }

  private boolean ignored(final String url) {
    for(String pattern: IGNORE_PATTERNS) {
      if (url.matches(pattern)) {
        return true;
      }
    }
    return false;
  }
}
