package blended.launcher.runtime;

import java.util.Properties;

import blended.launcher.BrandingProperties;

public class Branding {

	public static Properties getProperties() {
		try {
			return BrandingProperties.lastBrandingProperties();
		} catch (final Throwable e) {
			System.err.println("Could not access launcher branding properties. " + e.getMessage());
			return new Properties();
		}
	}

}
