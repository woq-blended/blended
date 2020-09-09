package blended.launcher;

import java.util.Map.Entry;
import java.util.Properties;

public class BrandingProperties {

	private static Properties lastBrandingProperties = new Properties();

	/* package */static void setLastBrandingProperties(final Properties properties) {
		lastBrandingProperties = properties;
	}

	public static Properties lastBrandingProperties() {
		final Properties props = new Properties();
		for (final Entry<Object, Object> prop : lastBrandingProperties.entrySet()) {
			props.setProperty((String) prop.getKey(), (String) prop.getValue());
		}
		return props;
	}

}
