package blended.launcher.runtime;

import blended.launcher.BrandingProperties;

import java.util.Properties;

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
