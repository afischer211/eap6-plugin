package com.redhat.plugin.eap6

import org.eclipse.xtend.lib.annotations.Accessors

/**
 * Manages Maven-coordinates
 */
class MavenGAV implements GAV{
	@Accessors final String groupId;
	@Accessors final String artifactId;
	@Accessors final String version;

	public new(String groupId, String artifactId, String version) {
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
	}

	override String getBaseVersion()
	{
		return extractBaseVersion(this.version)
	}

	def static public String extractBaseVersion(String version) {
		if(version==null) return null
		val sepIdx = version.indexOf("-");
		return version.substring(0, if(sepIdx > 0) sepIdx else version.length() - 1)
	}

	override public String toString() {

		val StringBuffer buf = new StringBuffer();
		buf.append('''«groupId»:«artifactId»''');
		if (version != null)
			buf.append(':').append(version);
		return buf.toString();
	}


}